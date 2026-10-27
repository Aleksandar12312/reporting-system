package com.example.monitor.api;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Decides per unit whether something is wrong with the scanner and with the recycler. Pure Java.
 *
 * <ul>
 *   <li>SCANNER: share of rejected notes (NOTE_REJECTED / READ_NOTE) compared with the median of the model.
 *       Something is wrong when the share is at least ranking.scanner.ratio times the median and the unit read
 *       at least min-reads notes and rejected at least min-rejects.</li>
 *   <li>RECYCLER: faults per day of the recycler fault kinds compared with the median of the model, at least
 *       ranking.recycler.min-faults faults in at least min-days days.</li>
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

    /** Scanner numbers of one unit: in the whole window, and only after the day it was last marked OK. */
    public record ScannerCounts(long unitId, int readsInWindow, int rejectedInWindow, int readsSinceOk,
                                int rejectedSinceOk) {
    }

    /** Recycler faults of one unit: in the whole window, and only after the day it was last marked OK. */
    public record RecyclerCounts(long unitId, int faultsInWindow, int faultsSinceOk) {
    }

    /** The newest "marked OK" of one unit and component. */
    public record LastOk(long unitId, IssueComponent component, LocalDateTime at, String by, String note) {
    }

    private static final double SECONDS_PER_DAY = 86_400.0;

    private final RankingProperties props;
    private final int baselineMinDays;

    /** @param baselineMinDays a unit needs this many days of history to count in the recycler median */
    public IssueEngine(RankingProperties props, int baselineMinDays) {
        this.props = props;
        this.baselineMinDays = baselineMinDays;
    }

    /** One entry per unit, each with one ComponentIssue per component (SCANNER, then RECYCLER). */
    public Map<Long, List<ComponentIssue>> evaluate(List<UnitWindow> units, List<ScannerCounts> scannerCounts,
                                                    List<RecyclerCounts> recyclerCounts, List<LastOk> lastOks) {
        Map<Long, ScannerCounts> scanner = new HashMap<>();
        scannerCounts.forEach(c -> scanner.put(c.unitId(), c));
        Map<Long, RecyclerCounts> recycler = new HashMap<>();
        recyclerCounts.forEach(c -> recycler.put(c.unitId(), c));
        Map<Long, Map<IssueComponent, LastOk>> oks = new HashMap<>();
        lastOks.forEach(ok -> oks.computeIfAbsent(ok.unitId(), k -> new EnumMap<>(IssueComponent.class))
                .put(ok.component(), ok));

        Map<String, Double> scannerMedians = medianPerModel(units, u -> {
            ScannerCounts c = scanner.get(u.unitId());
            return c != null && c.readsInWindow() >= props.scanner().minReads()
                    ? (double) c.rejectedInWindow() / c.readsInWindow()
                    : null;
        });
        Map<String, Double> recyclerMedians = medianPerModel(units, u -> {
            RecyclerCounts c = recycler.get(u.unitId());
            int faults = c == null ? 0 : c.faultsInWindow();
            return u.exposureDays() >= baselineMinDays && u.exposureDays() > 0 ? faults / u.exposureDays() : null;
        });

        Map<Long, List<ComponentIssue>> result = new HashMap<>();
        for (UnitWindow u : units) {
            Map<IssueComponent, LastOk> unitOks = oks.getOrDefault(u.unitId(), Map.of());
            result.put(u.unitId(), List.of(
                    scannerIssue(u, scanner.get(u.unitId()), scannerMedians.get(u.modelName()),
                            unitOks.get(IssueComponent.SCANNER)),
                    recyclerIssue(u, recycler.get(u.unitId()), recyclerMedians.get(u.modelName()),
                            unitOks.get(IssueComponent.RECYCLER))));
        }
        return result;
    }

    private ComponentIssue scannerIssue(UnitWindow u, ScannerCounts c, Double median, LastOk ok) {
        RankingProperties.ScannerRule rule = props.scanner();
        int reads = c == null ? 0 : (ok == null ? c.readsInWindow() : c.readsSinceOk());
        int rejected = c == null ? 0 : (ok == null ? c.rejectedInWindow() : c.rejectedSinceOk());
        Double share = reads > 0 ? (double) rejected / reads : null;
        Double ratio = ratio(share, median);
        boolean enoughData = reads >= rule.minReads();
        boolean wrong = enoughData && rejected >= rule.minRejects() && ratio != null && ratio >= rule.ratio();
        return issue(IssueComponent.SCANNER, IssueMetric.REJECT_SHARE, u, ok, wrong, share, median, ratio, reads,
                enoughData);
    }

    private ComponentIssue recyclerIssue(UnitWindow u, RecyclerCounts c, Double median, LastOk ok) {
        RankingProperties.RecyclerRule rule = props.recycler();
        LocalDateTime since = countingSince(u, ok);
        double exposureDays = ok == null
                ? u.exposureDays()
                : Math.max(0, Duration.between(since, u.computedAt()).getSeconds() / SECONDS_PER_DAY);
        int faults = c == null ? 0 : (ok == null ? c.faultsInWindow() : c.faultsSinceOk());
        Double rate = exposureDays > 0 ? faults / exposureDays : null;
        Double ratio = ratio(rate, median);
        boolean enoughData = exposureDays >= rule.minDays();
        boolean wrong = enoughData && faults >= rule.minFaults() && ratio != null && ratio >= rule.ratio();
        return issue(IssueComponent.RECYCLER, IssueMetric.FAULTS_PER_DAY, u, ok, wrong, rate, median, ratio, faults,
                enoughData);
    }

    private ComponentIssue issue(IssueComponent component, IssueMetric metric, UnitWindow u, LastOk ok,
                                 boolean wrong, Double value, Double median, Double ratio, int sample,
                                 boolean enoughData) {
        IssueStatus status;
        if (!wrong) {
            status = IssueStatus.OK;
        } else if (ok != null && !ok.at().isBefore(u.computedAt().minusDays(props.recurringDays()))) {
            status = IssueStatus.RECURRING;
        } else {
            status = IssueStatus.ACTIVE;
        }
        return new ComponentIssue(component, status, metric, value, median, ratio, sample, enoughData,
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

    private static Double ratio(Double value, Double median) {
        return value == null || median == null || median <= 0 ? null : value / median;
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
