package com.example.monitor.stats;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the statistics ETL (prefix "stats" in application-dev.yml). Every value has a default.
 *
 * @param enabled     false: the job, its schedule and the stats tables are not touched at all
 * @param refreshCron when to refresh the tables (UTC); the default runs 30 seconds after every mock tick
 * @param sliceSize   how many event ids one transaction of the unit_fault_daily step covers
 * @param chunkSize   how many units one transaction of the unit_stats step writes
 * @param windowDays      how far back to look; a younger unit is observed since it was created
 * @param baselineMinDays only units with at least this many days of data are used for the model median
 * @param faultSources    values of event.source that are real faults: the FAULT events of the recycler event list
 *                        (RecyclerEvent.faultSources(), table event_source); everything else is not a problem
 */
@ConfigurationProperties(prefix = "stats")
public record StatsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30 */5 * * * *") String refreshCron,
        @DefaultValue("500000") long sliceSize,
        @DefaultValue("500") int chunkSize,
        @DefaultValue("90") int windowDays,
        @DefaultValue("14") int baselineMinDays,
        @DefaultValue({"ERROR_DURING_PAYOUT", "COIN_MECH_ERROR", "COIN_MECH_JAMMED", "PAYOUT_OUT_OF_SERVICE", "JAMMED", "TIME_OUT",
                "INCOMPLETE_PAYOUT", "INCOMPLETE_FLOAT", "UNSAFE_NOTE_JAM", "SAFE_NOTE_JAM", "SOFTWARE_ERROR", "FAIL"}) List<String> faultSources) {

    public StatsProperties {
        if (refreshCron == null || refreshCron.isBlank()) {
            throw new IllegalArgumentException("stats.refresh-cron must not be empty");
        }
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
        if (faultSources == null || faultSources.isEmpty()) {
            throw new IllegalArgumentException("stats.fault-sources must contain at least one value");
        }
        faultSources = List.copyOf(faultSources);
    }

    /** What an empty "stats" section gives. */
    public static StatsProperties defaults() {
        return new StatsProperties(true, "30 */5 * * * *", 500_000, 500, 90, 14,
                List.of("ERROR_DURING_PAYOUT", "COIN_MECH_ERROR", "COIN_MECH_JAMMED", "PAYOUT_OUT_OF_SERVICE", "JAMMED", "TIME_OUT",
                "INCOMPLETE_PAYOUT", "INCOMPLETE_FLOAT", "UNSAFE_NOTE_JAM", "SAFE_NOTE_JAM", "SOFTWARE_ERROR", "FAIL"));
    }
}
