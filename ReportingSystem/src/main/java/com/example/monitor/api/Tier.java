package com.example.monitor.api;

/** How a unit compares with other units of the same model. The order is the order of "worst first". */
public enum Tier {
    /** Rate is at least ranking.critical-ratio times the model median. */
    CRITICAL,
    /** Rate is at least ranking.watch-ratio times the model median. */
    WATCH,
    /** Below the watch ratio, or fewer than ranking.min-faults faults. */
    OK,
    /** Enough faults, but no unit of this model has enough history to compute a median. */
    NO_BASELINE
}
