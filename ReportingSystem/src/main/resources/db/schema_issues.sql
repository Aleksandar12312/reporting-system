-- Tables of what users did. Created by the application on start; never filled or emptied by the statistics job.
-- Kept apart from the statistics tables on purpose: a statistics rebuild must never remove what users did.
-- Rows are only ever added; the newest row is the current state.
-- No foreign keys on purpose, like the statistics tables (schema.sql must be able to drop unit).

-- "Marked as OK" by a user, per unit and component (SCANNER, RECYCLER).
CREATE TABLE IF NOT EXISTS unit_issue_ack (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    unit_id    BIGINT        NOT NULL,
    component  VARCHAR(20)   NOT NULL,
    acked_at   DATETIME      NOT NULL,   -- UTC
    acked_by   VARCHAR(100)  NOT NULL,
    note       VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    KEY idx_uia_unit_component (unit_id, component, acked_at)
) ENGINE = InnoDB;

-- "Reset unit" by a user (e.g. after a service): from the day after the newest reset the unit is counted as if it
-- were new, for the tier and for every component. Its events stay in event; only the counting starts again.
CREATE TABLE IF NOT EXISTS unit_reset (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    unit_id    BIGINT        NOT NULL,
    reset_at   DATETIME      NOT NULL,   -- UTC
    reset_by   VARCHAR(100)  NOT NULL,
    note       VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    KEY idx_ur_unit (unit_id, reset_at)
) ENGINE = InnoDB;
