package com.example.monitor.recycler;

/**
 * What kind of event a recycler event is (column event_source.category).
 * Only FAULT events count as problems in the statistics.
 */
public enum EventCategory {
    /** The unit failed to do its job: jams, payout errors, failures. */
    FAULT,
    /** Needs attention but the unit is not broken: cashbox full or removed, fraud attempt, path open. */
    WARNING,
    /** A change of state: start-up, reset, disabled, a note cleared after power-up. */
    STATUS,
    /** Normal work: reading, accepting, storing, paying out notes. */
    OPERATION,
    /** A reply to a command of the host. */
    COMMAND
}
