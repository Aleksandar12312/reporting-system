package com.example.monitor.api;

/** The part of a machine a problem message is about. */
public enum IssueComponent {
    /** The note scanner (validator): judged by the share of rejected notes. */
    SCANNER,
    /** The recycler: payout errors and note jams, judged by faults per day. */
    RECYCLER
}
