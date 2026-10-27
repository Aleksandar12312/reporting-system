package com.example.monitor.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Thresholds that turn the statistics into a tier and a scope (prefix "ranking" in application-dev.yml).
 * The window, the baseline and the fault sources are not here: they belong to the statistics job (prefix "stats").
 * Every value has a default, so the section can be left out of the YAML completely.
 *
 * @param minFaults     fewer faults than this: the unit is never flagged
 * @param watchRatio    rate at least this many times the model median: WATCH
 * @param criticalRatio rate at least this many times the model median: CRITICAL
 * @param partMinFaults fewer faults than this: too few to say whether one part is the problem
 * @param partShare     one component with at least this share of the faults: scope PART
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
        @DefaultValue("3") int minFaults,
        @DefaultValue("2.0") double watchRatio,
        @DefaultValue("4.0") double criticalRatio,
        @DefaultValue("8") int partMinFaults,
        @DefaultValue("0.5") double partShare) {

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
    }

    /** What an empty "ranking" section gives. */
    public static RankingProperties defaults() {
        return new RankingProperties(3, 2.0, 4.0, 8, 0.5);
    }
}
