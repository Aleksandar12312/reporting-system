package com.example.monitor.api;

/**
 * Faults of one unit by component and fault name.
 *
 * @param part            component (event_source.part, e.g. RECYCLER)
 * @param fault           fault name (event.source)
 * @param pctOfUnitFaults share of all the unit's faults, in percent (0 to 100), rounded to one decimal
 */
public record FaultBreakdown(long unitId, String part, String fault, int faults, double pctOfUnitFaults) {
}
