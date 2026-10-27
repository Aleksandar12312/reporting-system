-- "Marked as OK" by a user, per unit and component (SCANNER, RECYCLER). Created by the application on start.
-- Kept apart from the statistics tables on purpose: a statistics rebuild must never remove what users did.
-- Rows are only ever added; the newest row per unit and component is the current state.
-- No foreign key on purpose, like the statistics tables (schema.sql must be able to drop unit).
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
