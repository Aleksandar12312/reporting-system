package com.example.monitor.api;

import java.time.LocalDateTime;

/** One row of unit_issue_ack: a user marked a component of a unit as OK. Times are UTC. */
public record IssueAck(long id, long unitId, String component, LocalDateTime ackedAt, String ackedBy,
                       String note) {
}
