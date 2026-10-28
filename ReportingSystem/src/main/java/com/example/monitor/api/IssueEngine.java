package com.example.monitor.api;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.example.monitor.api.RankingProperties.ComponentRule;

/**
 * Decides per unit and component (ranking.components) whether something is wrong. Pure Java.
 *
 * <ul>
 *   <li>SHARE (e.g. SCANNER): problems / work (rejected notes / notes read) compared with the median of the model.
 *       Something is wrong when the share is at least {@code ratio} times the median, with at least min-work work
 *       events and min-problems problem events.</li>
 *   <li>PER_DAY (e.g. RECYCLER): problem events per day compared with the median of the model, at least
 *       min-problems problems in at least min-days days.</li>
 * </ul>
 *
 * After a component was marked OK, only the days after that day count for that unit and component
 * ({@code countingSince}); the model medians always use the whole window of every unit. A problem found again
 * within ranking.recurring-days after "marked OK" is RECURRING.
 */
public final class IssueEngine {

    /** The window of one unit, from unit_stats. */
    public record UnitWindow(long unitId, String modelName, LocalDateTime windowStart, LocalDateTime computedAt,
                             double exposureDays) {
    }

    /**
     * Work and problem events of one unit for one component: in the whole window, and only after the day it was
     * last marked OK. PER_DAY components have no work events (0).
     */
    public record Counts(long unitId, int workInWindow, int problemsInWindow, int workSinceOk, int problemsSinceOk) {
    }

    /** The newest "marked OK" of one unit and component. */
    public record LastOk(long unitId, String component, LocalDateTime at, String by, String note) {
    }

    private static final double SECONDS_PER_DAY = 86_400.0;

    private final RankingProperties props;
    private final int baselineMinDays;

    /** @param baselineMinDays a unit needs this many days of history to count in a PER_DAY median */
    public IssueEngine(RankingProperties props, int baselineMinDays) {
        this.props = props;
        this.baselineMinDays = baselineMinDays;
    }

    /**
     * One entry per unit, each with one ComponentIssue per component, in the order of ranking.components.
     *
     * @param counts per component id, the counts of the units that have any of its events (others count as 0)
     */
    public Map<Long, List<ComponentIssue>> evaluate(List<UnitWindow> units, Map<String, List<Counts>> counts,
                                                    List<LastOk> lastOks) {
        Map<String, Map<Long, LastOk>> oks = new HashMap<>();
        lastOks.forEach(ok -> oks.computeIfAbsent(ok.component(), k -> new HashMap<>()).put(ok.unitId(), ok));

        Map<Long, List<ComponentIssue>> result = new HashMap<>();
        units.forEach(u -> result.put(u.unitId(), new ArrayList<>()));
        for (ComponentRule rule : props.components()) {
            Map<Long, Counts> byUnit = new HashMap<>();
            counts.getOrDefault(rule.id(), List.of()).forEach(c -> byUnit.put(c.unitId(), c));
            Map<String, Double> medians = medianPerModel(units, u -> modelValue(rule, u, byUnit.get(u.unitId())));
            Map<Long, LastOk> componentOks = oks.getOrDefault(rule.id(), Map.of());
            for (UnitWindow u : units) {
                result.get(u.unitId()).add(issueOf(rule, u, byUnit.get(u.unitId()), medians.get(u.modelName()),
                        componentOks.get(u.unitId())));
            }
        }
        result.replaceAll((id, issues) -> List.copyOf(issues));
        return result;
    }

    /** The unit's value over its whole window for the model median; null when it must not count. */
    private Double modelValue(ComponentRule rule, UnitWindow u, Counts c) {
        if (rule.metric() == IssueMetric.SHARE) {
            return c != null && c.workInWindow() >= rule.minWork()
                    ? (double) c.problemsInWindow() / c.workInWindow()
                    : null;
        }
        int problems = c == null ? 0 : c.problemsInWindow();
        return u.exposureDays() >= baselineMinDays && u.exposureDays() > 0 ? problems / u.exposureDays() : null;
    }

    private ComponentIssue issueOf(ComponentRule rule, UnitWindow u, Counts c, Double median, LastOk ok) {
        int problems = c == null ? 0 : (ok == null ? c.problemsInWindow() : c.problemsSinceOk());
        Double value;
        int sample;
        boolean enoughData;
        if (rule.metric() == IssueMetric.SHARE) {
            int work = c == null ? 0 : (ok == null ? c.workInWindow() : c.workSinceOk());
            value = work > 0 ? (double) problems / work : null;
            sample = work;
            enoughData = work >= rule.minWork();
        } else {
            double days = ok == null
                    ? u.exposureDays()
                    : Math.max(0, Duration.between(countingSince(u, ok), u.computedAt()).getSeconds() / SECONDS_PER_DAY);
            value = days > 0 ? problems / days : null;
            sample = problems;
            enoughData = days >= rule.minDays();
        }
        Double ratio = value == null || median == null || median <= 0 ? null : value / median;
        boolean wrong = enoughData && problems >= rule.minProblems() && ratio != null && ratio >= rule.ratio();

        IssueStatus status;
        if (!wrong) {
            status = IssueStatus.OK;
        } else if (ok != null && !ok.at().isBefore(u.computedAt().minusDays(props.recurringDays()))) {
            status = IssueStatus.RECURRING;
        } else {
            status = IssueStatus.ACTIVE;
        }
        return new ComponentIssue(rule.id(), status, rule.metric(), value, median, ratio, sample, enoughData,
                countingSince(u, ok),
                ok == null ? null : ok.at(), ok == null ? null : ok.by(), ok == null ? null : ok.note());
    }

    /** The start of the window, or the start of the day after the last "marked OK" if that is later. */
    static LocalDateTime countingSince(UnitWindow u, LastOk ok) {
        if (ok == null) {
            return u.windowStart();
        }
        LocalDateTime dayAfterOk = ok.at().toLocalDate().plusDays(1).atStartOfDay();
        return dayAfterOk.isAfter(u.windowStart()) ? dayAfterOk : u.windowStart();
    }

    /** Median of the non-null values per model; models without any value are left out. */
    private static Map<String, Double> medianPerModel(List<UnitWindow> units, Function<UnitWindow, Double> valueOf) {
        Map<String, List<Double>> byModel = new HashMap<>();
        for (UnitWindow u : units) {
            Double v = valueOf.apply(u);
            if (v != null) {
                byModel.computeIfAbsent(u.modelName(), k -> new ArrayList<>()).add(v);
            }
        }
        Map<String, Double> medians = new HashMap<>();
        byModel.forEach((model, values) -> medians.put(model, median(values)));
        return medians;
    }

    static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
