package com.example.monitor.api;

import java.time.LocalDateTime;

/**
 * The state of one component of one unit (part of {@link UnitScore#issues()}).
 *
 * @param component     id of the component as in ranking.components, e.g. SCANNER
 * @param status        OK, ACTIVE or RECURRING
 * @param metric        SHARE (value = problems / work, 0 to 1) or PER_DAY (value = problems per day)
 * @param value         the unit's value since countingSince; null when there is no data
 * @param modelMedian   median of the same value over the units of the same model (whole window); null if none
 * @param ratio         value divided by modelMedian; null when one of them is missing or the median is 0
 * @param sample        work events (SHARE, e.g. notes read) or problem events (PER_DAY, e.g. faults) since
 *                      countingSince
 * @param enoughData    false while too little has happened since countingSince to judge (for example right
 *                      after the component was marked OK)
 * @param countingSince start of the data the decision is based on (UTC): the start of the window, or the day
 *                      after the component was last marked OK
 * @param lastOkAt      when the component was last marked OK (UTC); null if never
 * @param lastOkBy      who marked it OK
 * @param lastOkNote    the note given then; may be null
 */
public record ComponentIssue(
        String component,
        IssueStatus status,
        IssueMetric metric,
        Double value,
        Double modelMedian,
        Double ratio,
        int sample,
        boolean enoughData,
        LocalDateTime countingSince,
        LocalDateTime lastOkAt,
        String lastOkBy,
        String lastOkNote) {
}
