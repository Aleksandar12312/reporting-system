package com.example.monitor.api;

/** How a component is judged (ranking.components[].metric), and so what the numbers of a {@link ComponentIssue} mean. */
public enum IssueMetric {
    /**
     * Problem events divided by work events (0 to 1), e.g. rejected notes / notes read for the scanner.
     * Fair to busy machines: a unit that works twice as much may have twice as many problems.
     */
    SHARE,
    /** Problem events per day, e.g. payout errors and note jams per day for the recycler. */
    PER_DAY
}
