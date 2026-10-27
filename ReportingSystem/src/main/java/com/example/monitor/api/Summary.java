package com.example.monitor.api;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Overview for the dashboard (GET /api/summary).
 *
 * @param computedAt when the statistics were last calculated, in UTC; null while unit_stats is still empty
 * @param windowDays the look-back window of the statistics (stats.window-days)
 * @param unitCount  number of units in unit_stats
 * @param tiers      number of units per tier; every tier is present, also with 0
 */
public record Summary(LocalDateTime computedAt, int windowDays, int unitCount, Map<Tier, Integer> tiers) {
}
