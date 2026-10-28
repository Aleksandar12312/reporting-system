package com.example.monitor.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One unit as read from unit + unit_event_daily (its faults) (input of the unit_stats step).
 *
 * @param unitId         the unit
 * @param unitTypeId     its model
 * @param countingFrom   the later of the creation date and the start of the day after the newest reset; may be null
 * @param faultsInWindow faults from the first day of the unit's window (never before countingFrom) up to today
 * @param totalFaults    every fault the unit ever had
 * @param lastFaultDate  day of the newest fault; null when the unit never had one
 */
public record UnitFaultTotals(long unitId, long unitTypeId, LocalDateTime countingFrom,
                              int faultsInWindow, int totalFaults, LocalDate lastFaultDate) {
}
