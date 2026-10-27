-- Read-only checks of the statistics tables (MySQL 8). Run them one at a time
-- (put the cursor in the query, Ctrl+Enter).
USE mock_lincstation;

-- 1. The daily table must hold exactly as many faults as the event table up to last_event_id (difference 0)
SELECT s.last_event_id,
       (SELECT COALESCE(SUM(faults), 0) FROM unit_fault_daily) AS in_daily_table,
       (SELECT COUNT(*) FROM event e
         WHERE e.id <= s.last_event_id
           AND e.source IN (SELECT fs.source FROM event_source fs WHERE fs.category = 'FAULT')) AS in_event_table,
       (SELECT MAX(id) FROM event) - s.last_event_id AS events_waiting_for_next_run
FROM stats_etl_state s;

-- 2. Every unit must have a unit_stats row (both numbers equal)
SELECT (SELECT COUNT(*) FROM unit) AS units, (SELECT COUNT(*) FROM unit_stats) AS unit_stats_rows;

-- 3. One row per model
SELECT * FROM model_stats ORDER BY model_name;

-- 4. Units with the highest rate compared with their model's median (a preview of what the scoring does)
SELECT s.unit_id, m.model_name, s.faults_in_window, ROUND(s.rate_per_day, 4) AS rate_per_day,
       ROUND(m.median_rate_per_day, 4) AS model_median, ROUND(s.rate_per_day / NULLIF(m.median_rate_per_day, 0), 2) AS ratio
FROM unit_stats s
JOIN model_stats m ON m.unit_type_id = s.unit_type_id
ORDER BY ratio DESC
LIMIT 20;

-- 5. Spring Batch: the last 10 runs of the job and how they ended
SELECT e.job_execution_id, e.start_time, e.end_time, e.status, e.exit_message
FROM BATCH_JOB_EXECUTION e
JOIN BATCH_JOB_INSTANCE i ON i.job_instance_id = e.job_instance_id
WHERE i.job_name = 'statsRefreshJob'
ORDER BY e.job_execution_id DESC
LIMIT 10;

-- 6. How many events of each category and name the units reported (the recycler event list)
SELECT s.category, e.source, COUNT(*) AS events
FROM event e
JOIN event_source s ON s.source = e.source
GROUP BY s.category, e.source
ORDER BY s.category, events DESC;
