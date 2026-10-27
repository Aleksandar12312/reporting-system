-- Answer key for the mock data: which behaviour the generator planted in each unit.
-- Run this together with schema.sql (it has no foreign key, so the order does not matter).
-- If you re-run schema.sql to start from empty tables, re-run this script as well.

USE mock_lincstation;

DROP TABLE IF EXISTS mock_unit_profile;

CREATE TABLE mock_unit_profile (
    unit_id  BIGINT      NOT NULL,
    profile  VARCHAR(30) NOT NULL,   -- NORMAL, BORDERLINE, BAD, BAD_ONE_COMPONENT, NEW_FEW_FAULTS, DIRTY_SCANNER, SILENT
    PRIMARY KEY (unit_id)
) ENGINE = InnoDB;
