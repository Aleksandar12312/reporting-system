package com.example.monitor.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of unit_stats. Every unit has a row, also a new one without any fault.
 *
 * @param unitId         the unit
 * @param unitTypeId     its model
 * @param windowStart    start of the observed window: now - window-days, or the creation date if the unit is younger
 * @param exposureDays   length of the window in days (with fractions)
 * @param faultsInWindow faults in the window
 * @param ratePerDay     faultsInWindow / exposureDays; 0 when the window is empty
 * @param inBaseline     true when exposureDays is at least baseline-min-days, so the unit counts in its model's median
 * @param totalFaults    every fault the unit ever had
 * @param lastFaultDate  day of the newest fault; null when the unit never had one
 * @param computedAt     when the row was calculated (UTC)
 */
public record UnitStats(long unitId, long unitTypeId, LocalDateTime windowStart, double exposureDays,
                        int faultsInWindow, double ratePerDay, boolean inBaseline, int totalFaults,
                        LocalDate lastFaultDate, LocalDateTime computedAt) {
}
