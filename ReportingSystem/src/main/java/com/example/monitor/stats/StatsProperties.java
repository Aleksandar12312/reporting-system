package com.example.monitor.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the statistics ETL (prefix "stats" in application-dev.yml). Every value has a default.
 * What counts as a fault is not a setting: it is every event with event_source.category = 'FAULT'.
 * The schedule (stats.refresh-cron) is read by StatsJobRunner itself.
 *
 * @param enabled     false: the job, its schedule and the stats tables are not touched at all
 * @param sliceSize   how many event ids one transaction of the unit_event_daily step covers
 * @param chunkSize   how many units one transaction of the unit_stats step writes
 * @param windowDays      how far back to look; a younger unit is observed since it was created
 * @param baselineMinDays only units with at least this many days of data are used for the model median
 */
@ConfigurationProperties(prefix = "stats")
public record StatsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500000") long sliceSize,
        @DefaultValue("500") int chunkSize,
        @DefaultValue("90") int windowDays,
        @DefaultValue("14") int baselineMinDays) {

    public StatsProperties {
        if (sliceSize <= 0) {
            throw new IllegalArgumentException("stats.slice-size must be positive");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("stats.chunk-size must be positive");
        }
        if (windowDays <= 0) {
            throw new IllegalArgumentException("stats.window-days must be positive");
        }
        if (baselineMinDays < 0) {
            throw new IllegalArgumentException("stats.baseline-min-days must not be negative");
        }
    }

    /** What an empty "stats" section gives. */
    public static StatsProperties defaults() {
        return new StatsProperties(true, 500_000, 500, 90, 14);
    }
}
