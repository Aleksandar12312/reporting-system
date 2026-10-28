package com.example.monitor.api;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Thresholds that turn the statistics into a tier, a scope and component messages
 * (prefix "ranking" in application-dev.yml).
 * The window, the baseline and the fault sources are not here: they belong to the statistics job (prefix "stats").
 * Every value has a default, so the section can be left out of the YAML completely.
 *
 * @param minFaults     fewer faults than this: the unit is never flagged
 * @param watchRatio    rate at least this many times the model median: WATCH
 * @param criticalRatio rate at least this many times the model median: CRITICAL
 * @param partMinFaults fewer faults than this: too few to say whether one part is the problem
 * @param partShare     one component with at least this share of the faults: scope PART
 * @param components    the parts of a machine that get their own message (SCANNER, RECYCLER ...), in the order the
 *                      API returns them; left out = {@link #defaultComponents()}
 * @param recurringDays a problem that comes back within this many days after "marked OK" is RECURRING
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
        @DefaultValue("3") int minFaults,
        @DefaultValue("2.0") double watchRatio,
        @DefaultValue("4.0") double criticalRatio,
        @DefaultValue("8") int partMinFaults,
        @DefaultValue("0.5") double partShare,
        List<ComponentRule> components,
        @DefaultValue("14") int recurringDays) {

    public RankingProperties {
        if (minFaults < 0) {
            throw new IllegalArgumentException("ranking.min-faults must not be negative");
        }
        if (watchRatio <= 0) {
            throw new IllegalArgumentException("ranking.watch-ratio must be positive");
        }
        if (criticalRatio < watchRatio) {
            throw new IllegalArgumentException("ranking.critical-ratio must not be smaller than ranking.watch-ratio");
        }
        if (partMinFaults < 1) {
            throw new IllegalArgumentException("ranking.part-min-faults must be at least 1");
        }
        if (partShare <= 0 || partShare > 1) {
            throw new IllegalArgumentException("ranking.part-share must be greater than 0 and at most 1");
        }
        if (recurringDays < 0) {
            throw new IllegalArgumentException("ranking.recurring-days must not be negative");
        }
        components = components == null || components.isEmpty() ? defaultComponents() : List.copyOf(components);
        Set<String> ids = new HashSet<>();
        for (ComponentRule c : components) {
            if (!ids.add(c.id())) {
                throw new IllegalArgumentException("ranking.components: id " + c.id() + " is used twice");
            }
        }
    }

    /** What an empty "ranking" section gives. */
    public static RankingProperties defaults() {
        return new RankingProperties(3, 2.0, 4.0, 8, 0.5, defaultComponents(), 14);
    }

    /**
     * SCANNER: share of rejected notes (NOTE_REJECTED / READ_NOTE).
     * RECYCLER: faults per day of the FAULT events whose event_source.part is RECYCLER (payout errors, note jams).
     */
    public static List<ComponentRule> defaultComponents() {
        return List.of(
                new ComponentRule("SCANNER", IssueMetric.SHARE, List.of("READ_NOTE"), List.of("NOTE_REJECTED"),
                        30, 3, 5, 2.0),
                new ComponentRule("RECYCLER", IssueMetric.PER_DAY, List.of(), List.of(), 30, 3, 5, 2.0));
    }

    /**
     * When something is wrong with one part of a machine (one entry of ranking.components).
     * The unit's value is compared with the median of the units of the same model; something is wrong when it is
     * at least {@code ratio} times the median and there is enough data to judge.
     *
     * @param id          name of the part, e.g. SCANNER; capital letters, digits and _, at most 20 characters.
     *                    It is used in the URL of "mark as OK" and stored in unit_issue_ack.component
     * @param metric      SHARE: problems / work. PER_DAY: problems per day
     * @param work        SHARE only: event names that count the work (e.g. READ_NOTE); must not be empty
     * @param problems    event names that count the problems (e.g. NOTE_REJECTED). Left out with PER_DAY: every
     *                    FAULT event whose event_source.part equals the id
     * @param minWork     SHARE: at least this many work events (in the window, or since "marked OK")
     * @param minDays     PER_DAY: at least this many days of data (in the window, or since "marked OK")
     * @param minProblems at least this many problem events
     * @param ratio       value at least this many times the model median: something is wrong
     */
    public record ComponentRule(
            String id,
            @DefaultValue("PER_DAY") IssueMetric metric,
            @DefaultValue List<String> work,
            @DefaultValue List<String> problems,
            @DefaultValue("30") int minWork,
            @DefaultValue("3") int minDays,
            @DefaultValue("5") int minProblems,
            @DefaultValue("2.0") double ratio) {

        private static final Pattern ID = Pattern.compile("[A-Z][A-Z0-9_]{0,19}");

        public ComponentRule {
            id = id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
            if (!ID.matcher(id).matches()) {
                throw new IllegalArgumentException("ranking.components: id '" + id
                        + "' must start with a letter and have only letters, digits and _ (at most 20 characters)");
            }
            metric = metric == null ? IssueMetric.PER_DAY : metric;
            work = clean(work);
            problems = clean(problems);
            if (metric == IssueMetric.SHARE && (work.isEmpty() || problems.isEmpty())) {
                throw new IllegalArgumentException("ranking.components " + id + ": metric SHARE needs work and problems");
            }
            if (minWork < 1 || minDays < 0 || minProblems < 0 || ratio <= 0) {
                throw new IllegalArgumentException("ranking.components " + id
                        + ": min-work must be at least 1, min-days and min-problems not negative, ratio positive");
            }
        }

        private static List<String> clean(List<String> names) {
            if (names == null) {
                return List.of();
            }
            return names.stream().map(String::trim).filter(n -> !n.isEmpty()).distinct().toList();
        }
    }
}
