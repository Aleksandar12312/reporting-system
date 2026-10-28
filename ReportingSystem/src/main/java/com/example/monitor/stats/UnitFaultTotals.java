package com.example.monitor.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One unit as read from unit + unit_event_daily (its faults) (input of the unit_stats step).
 *
 * @param unitId         the unit
 * @param unitTypeId     its model
 * @param creationDate   when the unit was created; may be null
 * @param faultsInWindow faults from the first day of the unit's window up to today
 * @param totalFaults    every fault the unit ever had
 * @param lastFaultDate  day of the newest fault; null when the unit never had one
 */
public record UnitFaultTotals(long unitId, long unitTypeId, LocalDateTime creationDate,
                              int faultsInWindow, int totalFaults, LocalDate lastFaultDate) {
}
