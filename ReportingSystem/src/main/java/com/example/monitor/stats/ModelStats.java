package com.example.monitor.stats;

import java.time.LocalDateTime;

/**
 * One row of model_stats.
 *
 * @param unitTypeId        the model
 * @param modelName         its name
 * @param windowDays        the window used (stats.window-days)
 * @param unitCount         all units of the model
 * @param baselineUnitCount units with at least baseline-min-days of history; only they form the median and the mean
 * @param faultsInWindow    faults of all units of the model in their windows
 * @param medianRatePerDay  median faults per day of the baseline units; null when there are none
 * @param meanRatePerDay    mean faults per day of the baseline units; null when there are none
 * @param computedAt        when the row was calculated (UTC)
 */
public record ModelStats(long unitTypeId, String modelName, int windowDays, int unitCount, int baselineUnitCount,
                         long faultsInWindow, Double medianRatePerDay, Double meanRatePerDay,
                         LocalDateTime computedAt) {
}
