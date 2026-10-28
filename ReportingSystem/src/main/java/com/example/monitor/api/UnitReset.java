package com.example.monitor.api;

import java.time.LocalDateTime;

/**
 * One row of unit_reset: a user reset a unit (e.g. after a service). Times are UTC.
 *
 * @param countingFrom start of the day after the reset: from then on the unit is counted as if it were new
 */
public record UnitReset(long id, long unitId, LocalDateTime resetAt, String resetBy, String note,
                        LocalDateTime countingFrom) {
}
