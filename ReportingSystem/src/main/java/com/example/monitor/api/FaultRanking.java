package com.example.monitor.api;

/**
 * One unit in the ranking of a single fault kind, counted in the unit's window.
 *
 * @param fault        the fault the ranking is about, e.g. JAMMED
 * @param faults       how many times this unit had that fault in its window
 * @param exposureDays how long the unit was observed in the window
 * @param ratePerDay   faults divided by exposure days
 */
public record FaultRanking(
        long unitId,
        String serialNumber,
        String modelName,
        String fault,
        int faults,
        double exposureDays,
        double ratePerDay) {
}
