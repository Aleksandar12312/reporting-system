package com.example.monitor.stats;

import java.time.LocalDateTime;

/**
 * The single row of stats_etl_state: how far the event table has been read into unit_fault_daily.
 *
 * The check event is the newest event with an id up to {@code lastEventId}, remembered with its unit and time.
 * If that event is gone or different on the next run, the event table was re-created (re-seed) and the
 * statistics no longer match it, so they are built again from the start.
 *
 * @param lastEventId    every event with an id up to this value is counted in unit_fault_daily
 * @param checkEventId   id of the check event; null when no event had been read yet
 * @param checkUnitId    unit of the check event
 * @param checkEventTime time of the check event
 */
public record EtlState(long lastEventId, Long checkEventId, Long checkUnitId, LocalDateTime checkEventTime) {

    public boolean hasCheckEvent() {
        return checkEventId != null;
    }
}
