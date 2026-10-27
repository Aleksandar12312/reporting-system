package com.example.monitor.api;

/** For a flagged unit: is one component the problem, or the whole unit? */
public enum ProblemScope {
    /** One component causes at least ranking.part-share of the unit's faults. */
    PART,
    /** The faults are spread over many components. */
    GENERAL
}
