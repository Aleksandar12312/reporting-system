package com.example.monitor.stats;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.batch.item.ItemProcessor;

/**
 * Step 3 of the job (processor): turns the fault counts of one unit into its unit_stats row.
 * The window is the last window-days days, or shorter for a younger or reset unit,
 * and only a unit with at least baseline-min-days of history counts in its model's median.
 */
public class UnitStatsProcessor implements ItemProcessor<UnitFaultTotals, UnitStats> {

    private static final double SECONDS_PER_DAY = 86_400.0;

    private final LocalDateTime runAt;
    private final int windowDays;
    private final int baselineMinDays;

    public UnitStatsProcessor(LocalDateTime runAt, int windowDays, int baselineMinDays) {
        this.runAt = runAt;
        this.windowDays = windowDays;
        this.baselineMinDays = baselineMinDays;
    }

    @Override
    public UnitStats process(UnitFaultTotals unit) {
        LocalDateTime windowStart = runAt.minusDays(windowDays);
        if (unit.countingFrom() != null && unit.countingFrom().isAfter(windowStart)) {
            windowStart = unit.countingFrom();
        }
        // A start in the future (e.g. reset today: counting starts tomorrow) gives an empty window, not a negative one
        double exposureDays = Math.max(0, Duration.between(windowStart, runAt).getSeconds() / SECONDS_PER_DAY);
        double rate = exposureDays > 0 ? unit.faultsInWindow() / exposureDays : 0;
        return new UnitStats(unit.unitId(), unit.unitTypeId(), windowStart, exposureDays, unit.faultsInWindow(),
                rate, exposureDays >= baselineMinDays, unit.totalFaults(), unit.lastFaultDate(), runAt);
    }
}
