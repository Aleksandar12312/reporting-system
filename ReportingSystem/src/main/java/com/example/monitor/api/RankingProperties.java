package com.example.monitor.api;

import java.util.List;

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
 * @param scanner       when something is wrong with the scanner
 * @param recycler      when something is wrong with the recycler
 * @param recurringDays a problem that comes back within this many days after "marked OK" is RECURRING
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
        @DefaultValue("3") int minFaults,
        @DefaultValue("2.0") double watchRatio,
        @DefaultValue("4.0") double criticalRatio,
        @DefaultValue("8") int partMinFaults,
        @DefaultValue("0.5") double partShare,
        @DefaultValue ScannerRule scanner,
        @DefaultValue RecyclerRule recycler,
        @DefaultValue("14") int recurringDays) {

    /** Default fault kinds of the recycler: payout errors and note jams. */
    public static final List<String> DEFAULT_RECYCLER_FAULTS = List.of(
            "ERROR_DURING_PAYOUT", "INCOMPLETE_PAYOUT", "INCOMPLETE_FLOAT", "PAYOUT_OUT_OF_SERVICE", "TIME_OUT",
            "SAFE_NOTE_JAM", "UNSAFE_NOTE_JAM", "JAMMED");

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
        scanner = scanner == null ? ScannerRule.defaults() : scanner;
        recycler = recycler == null ? RecyclerRule.defaults() : recycler;
    }

    /** What an empty "ranking" section gives. */
    public static RankingProperties defaults() {
        return new RankingProperties(3, 2.0, 4.0, 8, 0.5, ScannerRule.defaults(), RecyclerRule.defaults(), 14);
    }

    /**
     * Something is wrong with the scanner when its share of rejected notes is at least {@code ratio} times the
     * median of its model, and it read and rejected enough notes to judge.
     */
    public record ScannerRule(
            @DefaultValue("30") int minReads,
            @DefaultValue("5") int minRejects,
            @DefaultValue("2.0") double ratio) {

        public ScannerRule {
            if (minReads < 1 || minRejects < 0 || ratio <= 0) {
                throw new IllegalArgumentException(
                        "ranking.scanner: min-reads must be at least 1, min-rejects not negative, ratio positive");
            }
        }

        public static ScannerRule defaults() {
            return new ScannerRule(30, 5, 2.0);
        }
    }

    /**
     * Something is wrong with the recycler when its faults per day (of the given fault kinds) are at least
     * {@code ratio} times the median of its model, with at least {@code minFaults} faults in at least
     * {@code minDays} days.
     */
    public record RecyclerRule(
            @DefaultValue("3") int minDays,
            @DefaultValue("5") int minFaults,
            @DefaultValue("2.0") double ratio,
            @DefaultValue({"ERROR_DURING_PAYOUT", "INCOMPLETE_PAYOUT", "INCOMPLETE_FLOAT", "PAYOUT_OUT_OF_SERVICE",
                    "TIME_OUT", "SAFE_NOTE_JAM", "UNSAFE_NOTE_JAM", "JAMMED"}) List<String> faults) {

        public RecyclerRule {
            if (minDays < 0 || minFaults < 0 || ratio <= 0) {
                throw new IllegalArgumentException(
                        "ranking.recycler: min-days and min-faults must not be negative, ratio must be positive");
            }
            if (faults == null || faults.isEmpty()) {
                throw new IllegalArgumentException("ranking.recycler.faults must contain at least one value");
            }
            faults = List.copyOf(faults);
        }

        public static RecyclerRule defaults() {
            return new RecyclerRule(3, 5, 2.0, DEFAULT_RECYCLER_FAULTS);
        }
    }
}
