package com.example.monitor.api;

import java.time.LocalDateTime;

/**
 * One row of model_stats as the REST API returns it (GET /api/models).
 *
 * @param medianRatePerDay median faults per day of the model's baseline units; null if it has none
 * @param meanRatePerDay   mean faults per day of the model's baseline units; null if it has none
 * @param computedAt       when the row was calculated, in UTC
 */
public record ModelStatsRow(
        long unitTypeId,
        String modelName,
        int windowDays,
        int unitCount,
        int baselineUnitCount,
        long faultsInWindow,
        Double medianRatePerDay,
        Double meanRatePerDay,
        LocalDateTime computedAt) {
}
