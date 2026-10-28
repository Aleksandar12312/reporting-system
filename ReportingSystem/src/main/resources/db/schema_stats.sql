-- Statistics tables (MySQL 8). Filled and kept up to date by the Spring Batch job statsRefreshJob
-- (package com.example.monitor.stats).
--
-- The application runs this script itself on every start, so you do not have to.
-- Every CREATE is CREATE TABLE IF NOT EXISTS: running it again never removes data of the current tables
-- (only the two old tables replaced by unit_event_daily are dropped).
-- To run it by hand in Workbench, first make mock_lincstation the default schema (double-click it).
--
-- No foreign keys on purpose: these tables hold derived numbers only, and foreign keys would stop
-- schema.sql from dropping unit/unit_type when the mock data is created again.

-- Replaced by unit_event_daily (one table for every kind of event). They held derived numbers only.
DROP TABLE IF EXISTS unit_fault_daily;
DROP TABLE IF EXISTS unit_scanner_daily;

-- Events per unit, day (UTC day of event.event_time) and event name (event.source): every event that has a
-- source is counted, not only the faults. Faults, scanner reads and rejects, payouts ... are all rows here,
-- so a new component (ranking.components) never needs a new table and never a rebuild.
-- Which component an event belongs to is NOT stored here: it comes from event_source.part, joined when reading,
-- so changing event_source.part takes effect immediately.
CREATE TABLE IF NOT EXISTS unit_event_daily (
    unit_id      BIGINT       NOT NULL,
    event_date   DATE         NOT NULL,
    source       VARCHAR(40)  NOT NULL,
    event_count  INT          NOT NULL,
    PRIMARY KEY (unit_id, event_date, source),
    KEY idx_ued_source_date (source, event_date),
    KEY idx_ued_date (event_date)
) ENGINE = InnoDB;

-- One row per unit, also for a unit without faults. The window is the last stats.window-days days,
-- or shorter for a younger unit; faults (stats.fault-sources) are counted from the first day of the window.
CREATE TABLE IF NOT EXISTS unit_stats (
    unit_id           BIGINT    NOT NULL,
    unit_type_id      BIGINT    NOT NULL,
    window_start      DATETIME  NOT NULL,
    exposure_days     DOUBLE    NOT NULL,
    faults_in_window  INT       NOT NULL,
    rate_per_day      DOUBLE    NOT NULL,
    in_baseline       BOOLEAN   NOT NULL,   -- at least stats.baseline-min-days of history: counts in the median
    total_faults      INT       NOT NULL,   -- every fault the unit ever had
    last_fault_date   DATE      NULL,       -- NULL: the unit never had a fault
    computed_at       DATETIME  NOT NULL,   -- UTC
    PRIMARY KEY (unit_id),
    KEY idx_us_type (unit_type_id)
) ENGINE = InnoDB;

-- One row per model. Median and mean use only the baseline units; NULL when the model has none.
CREATE TABLE IF NOT EXISTS model_stats (
    unit_type_id         BIGINT       NOT NULL,
    model_name           VARCHAR(63)  NOT NULL,
    window_days          INT          NOT NULL,
    unit_count           INT          NOT NULL,
    baseline_unit_count  INT          NOT NULL,
    faults_in_window     BIGINT       NOT NULL,
    median_rate_per_day  DOUBLE       NULL,
    mean_rate_per_day    DOUBLE       NULL,
    computed_at          DATETIME     NOT NULL,
    PRIMARY KEY (unit_type_id)
) ENGINE = InnoDB;

-- One row (id = 1): every event with an id up to last_event_id is counted in unit_event_daily.
-- check_* is the newest of those events; if it disappears or changes, the event table was re-created
-- and the job builds all statistics again from the start.
CREATE TABLE IF NOT EXISTS stats_etl_state (
    id                TINYINT   NOT NULL,
    last_event_id     BIGINT    NOT NULL,
    check_event_id    BIGINT    NULL,
    check_unit_id     BIGINT    NULL,
    check_event_time  DATETIME  NULL,
    updated_at        DATETIME  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
