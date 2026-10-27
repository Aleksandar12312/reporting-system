package com.example.monitor.api;

/** What the numbers of a {@link ComponentIssue} mean. */
public enum IssueMetric {
    /** Rejected notes divided by notes read (0 to 1). */
    REJECT_SHARE,
    /** Faults of the component per day. */
    FAULTS_PER_DAY
}
