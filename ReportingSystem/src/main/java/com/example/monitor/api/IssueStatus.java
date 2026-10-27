package com.example.monitor.api;

/** State of one component of one unit. */
public enum IssueStatus {
    /** Nothing wrong, not enough data yet, or marked OK and nothing new since. */
    OK,
    /** Something is wrong with the component. */
    ACTIVE,
    /** Something is wrong again, within ranking.recurring-days after it was marked OK. */
    RECURRING
}
