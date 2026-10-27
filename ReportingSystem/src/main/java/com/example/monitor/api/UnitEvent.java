package com.example.monitor.api;

import java.time.LocalDateTime;

/**
 * One row of the event table.
 *
 * @param eventTime when it happened, in UTC (sent as "2026-10-26T12:00:48", without a zone)
 * @param type      event type (event.type), may be null
 * @param part      component (event.placeholder), may be null
 * @param source    what happened (event.source), for faults e.g. JAMMED
 */
public record UnitEvent(long id, long unitId, LocalDateTime eventTime, String type, String part, String source) {
}
