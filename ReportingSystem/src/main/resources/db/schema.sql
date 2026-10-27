-- Mock schema for the problematic-units project (MySQL 8)
-- Six tables: unit_type, address, location, unit, event_source, event
-- Running it again deletes ALL data (also the statistics tables, which the application then builds again).
-- Remove the first two statements if your tool (e.g. Flyway) already uses a schema.
CREATE DATABASE IF NOT EXISTS mock_lincstation;
USE mock_lincstation;

-- Drop in reverse dependency order so the script can be re-run.
-- The statistics tables hold numbers derived from event; the application creates and fills them again.
DROP TABLE IF EXISTS unit_issue_ack;
DROP TABLE IF EXISTS unit_scanner_daily;
DROP TABLE IF EXISTS unit_fault_daily;
DROP TABLE IF EXISTS unit_stats;
DROP TABLE IF EXISTS model_stats;
DROP TABLE IF EXISTS stats_etl_state;
DROP TABLE IF EXISTS event;
DROP TABLE IF EXISTS event_source;
DROP TABLE IF EXISTS unit;
DROP TABLE IF EXISTS location;
DROP TABLE IF EXISTS address;
DROP TABLE IF EXISTS unit_type;

-- Machine models (e.g. Duo, Pro, Recycler Max)
CREATE TABLE unit_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    model_name  VARCHAR(63)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_unit_type_model_name (model_name)
) ENGINE = InnoDB;

-- City and country of a site
CREATE TABLE address (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    city          VARCHAR(63)  NOT NULL,
    country_code  CHAR(2)      NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- Sites where machines are installed
CREATE TABLE location (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(127)  NOT NULL,
    address_id  BIGINT        NOT NULL,
    PRIMARY KEY (id),
    KEY idx_location_address (address_id),
    CONSTRAINT fk_location_address
        FOREIGN KEY (address_id) REFERENCES address (id)
) ENGINE = InnoDB;

-- One physical machine
CREATE TABLE unit (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    serial_number  VARCHAR(31)  NOT NULL,
    unit_type_id   BIGINT       NOT NULL,
    location_id    BIGINT       NOT NULL,
    creation_date  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_unit_serial_number (serial_number),
    KEY idx_unit_type (unit_type_id),
    KEY idx_unit_location (location_id),
    CONSTRAINT fk_unit_unit_type
        FOREIGN KEY (unit_type_id) REFERENCES unit_type (id),
    CONSTRAINT fk_unit_location
        FOREIGN KEY (location_id) REFERENCES location (id)
) ENGINE = InnoDB;

-- Every event name a bill recycler can report (the generic recycler event list, codes 176-250).
-- event.source must be one of these names (foreign key), so a misspelled name is refused instead of
-- being stored and silently ignored. The same list is in Java: com.example.monitor.recycler.RecyclerEvent.
--   category  FAULT      the unit failed (only these count in the statistics)
--             WARNING    needs attention, the unit is not broken (cashbox full/removed, fraud attempt ...)
--             STATUS     a change of state (start-up, reset, disabled, note cleared after power-up ...)
--             OPERATION  normal work (reading, storing, paying out notes)
--             COMMAND    a reply to a command of the host
CREATE TABLE event_source (
    source       VARCHAR(40)   NOT NULL,
    code         SMALLINT      NOT NULL,
    category     VARCHAR(10)   NOT NULL,
    description  VARCHAR(100)  NOT NULL,
    PRIMARY KEY (source),
    UNIQUE KEY uq_event_source_code (code),
    KEY idx_event_source_category (category),
    CONSTRAINT chk_event_source_category
        CHECK (category IN ('FAULT', 'WARNING', 'STATUS', 'OPERATION', 'COMMAND'))
) ENGINE = InnoDB;

INSERT INTO event_source (code, source, category, description) VALUES
    (176, 'JAM_RECOVERY', 'WARNING', 'The unit is recovering from a jam'),
    (177, 'ERROR_DURING_PAYOUT', 'FAULT', 'An error stopped a payout'),
    (179, 'SMART_EMPTYING', 'OPERATION', 'Smart emptying of the payout to the cashbox is running'),
    (180, 'SMART_EMPTIED', 'OPERATION', 'Smart emptying finished'),
    (181, 'CHANNEL_DISABLE', 'STATUS', 'All note channels are disabled by the host'),
    (182, 'INITIALISING', 'STATUS', 'The unit is starting up'),
    (183, 'COIN_MECH_ERROR', 'FAULT', 'Coin mechanism error (coin devices only)'),
    (194, 'EMPTYING', 'OPERATION', 'Emptying of the payout to the cashbox is running'),
    (195, 'EMPTIED', 'OPERATION', 'Emptying finished'),
    (196, 'COIN_MECH_JAMMED', 'FAULT', 'Coin mechanism jammed (coin devices only)'),
    (197, 'COIN_MECH_RETURN_PRESSED', 'OPERATION', 'Coin return button pressed (coin devices only)'),
    (198, 'PAYOUT_OUT_OF_SERVICE', 'FAULT', 'The payout (recycler) part is out of service'),
    (199, 'NOTE_FLOAT_REMOVED', 'WARNING', 'The note float (recycler module) was removed'),
    (200, 'NOTE_FLOAT_ATTACHED', 'STATUS', 'The note float (recycler module) was attached'),
    (201, 'NOTE_TRANSFERED_TO_STACKER', 'OPERATION', 'A note was moved from the payout to the cashbox'),
    (202, 'NOTE_PAID_INTO_STACKER_AT_POWER-UP', 'STATUS', 'A note found in the path at power-up went to the cashbox'),
    (203, 'NOTE_PAID_INTO_STORE_AT_POWER-UP', 'STATUS', 'A note found in the path at power-up went to the payout store'),
    (204, 'NOTE_STACKING', 'OPERATION', 'A note is being moved to its storage'),
    (205, 'NOTE_DISPENSED_AT_POWER-UP', 'STATUS', 'A note found in the path at power-up was given back'),
    (206, 'NOTE_HELD_IN_BEZEL', 'WARNING', 'A paid-out note waits in the bezel for the customer'),
    (209, 'BAR_CODE_TICKET_ACKNOWLEDGE', 'OPERATION', 'A bar code ticket was accepted (ticket devices only)'),
    (210, 'DISPENSED', 'OPERATION', 'A payout finished'),
    (213, 'JAMMED', 'FAULT', 'A note is jammed'),
    (214, 'HALTED', 'OPERATION', 'A payout was stopped by the host'),
    (215, 'FLOATING', 'OPERATION', 'Floating (moving notes to reach a target level) is running'),
    (216, 'FLOATED', 'OPERATION', 'Floating finished'),
    (217, 'TIME_OUT', 'FAULT', 'A payout timed out (a note could not be found)'),
    (218, 'DISPENSING', 'OPERATION', 'A payout is running'),
    (219, 'NOTE_STORED_IN_PAYOUT', 'OPERATION', 'An accepted note was stored for recycling'),
    (220, 'INCOMPLETE_PAYOUT', 'FAULT', 'A payout ended with less money than requested'),
    (221, 'INCOMPLETE_FLOAT', 'FAULT', 'Floating ended before the target was reached'),
    (222, 'CASHBOX_PAID', 'OPERATION', 'Notes were moved to the cashbox during a payout or float'),
    (223, 'COIN_CREDIT', 'OPERATION', 'A coin was credited (coin devices only)'),
    (224, 'NOTE_PATH_OPEN', 'WARNING', 'The note path is open'),
    (225, 'NOTE_CLEARED_FROM_FRONT', 'STATUS', 'A note found at power-up was rejected to the front'),
    (226, 'NOTE_CLEARED_TO_CASHBOX', 'STATUS', 'A note found at power-up was moved to the cashbox'),
    (227, 'CASHBOX_REMOVED', 'WARNING', 'The cashbox was removed'),
    (228, 'CASHBOX_REPLACED', 'STATUS', 'The cashbox was put back'),
    (229, 'BAR_CODE_TICKET_VALIDATED', 'OPERATION', 'A bar code ticket was read (ticket devices only)'),
    (230, 'FRAUD_ATTEMPT', 'WARNING', 'A fraud attempt was detected'),
    (231, 'STACKER_FULL', 'WARNING', 'The cashbox (stacker) is full'),
    (232, 'DISABLED', 'STATUS', 'The unit is disabled'),
    (233, 'UNSAFE_NOTE_JAM', 'FAULT', 'A note is jammed where the customer can reach it'),
    (234, 'SAFE_NOTE_JAM', 'FAULT', 'A note is jammed inside the unit, out of the customer''s reach'),
    (235, 'NOTE_STACKED', 'OPERATION', 'A note reached the cashbox'),
    (236, 'NOTE_REJECTED', 'OPERATION', 'A note was refused and given back'),
    (237, 'NOTE_REJECTING', 'OPERATION', 'A note is being refused'),
    (238, 'CREDIT_NOTE', 'OPERATION', 'An accepted note was credited'),
    (239, 'READ_NOTE', 'OPERATION', 'A note is being read'),
    (240, 'OK', 'COMMAND', 'Reply: command done'),
    (241, 'SLAVE_RESET', 'STATUS', 'The unit restarted'),
    (242, 'COMMAND_NOT_KNOWN', 'COMMAND', 'Reply: unknown command'),
    (243, 'WRONG_NO_PARAMETERS', 'COMMAND', 'Reply: wrong number of parameters'),
    (244, 'PARAMETER_OUT_OF_RANGE', 'COMMAND', 'Reply: parameter out of range'),
    (245, 'COMMAND_CANNOT_BE_PROCESSED', 'COMMAND', 'Reply: command cannot be processed now'),
    (246, 'SOFTWARE_ERROR', 'FAULT', 'Software error in the unit'),
    (248, 'FAIL', 'FAULT', 'General failure'),
    (250, 'KEY_NOT_SET', 'WARNING', 'The encryption key is not set');

-- Append-only log of everything a machine reports.
-- Every row is standalone: there is no link between events (a JAM_RECOVERY is its own row).
--   type        family of the event; the mock uses PLACEHOLDER (an event about a component)
--   placeholder the component the event is about; the mock uses RC (the bill recycler)
--   source      the specific event name, one of event_source (e.g. SAFE_NOTE_JAM, NOTE_STACKED)
-- The columns are VARCHAR(40): the longest event name (NOTE_PAID_INTO_STACKER_AT_POWER-UP) has 34 characters,
-- and MySQL 8 in strict mode refuses a longer value instead of cutting it.
-- The real EVENT table also has a STATUS column; it is intentionally left out of this mock.
-- event_time is stored in UTC (the real DATETIME column is machine-local time;
-- the mock skips that complication on purpose).
CREATE TABLE event (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    unit_id      BIGINT       NOT NULL,
    event_time   DATETIME     NOT NULL,
    type         VARCHAR(40)  NULL,
    placeholder  VARCHAR(40)  NULL,
    source       VARCHAR(40)  NULL,
    PRIMARY KEY (id),
    KEY idx_event_unit_time (unit_id, event_time),
    KEY idx_event_unit_component_time (unit_id, placeholder, event_time, id),
    KEY idx_event_time (event_time),
    KEY idx_event_source (source),
    CONSTRAINT fk_event_unit
        FOREIGN KEY (unit_id) REFERENCES unit (id),
    CONSTRAINT fk_event_source
        FOREIGN KEY (source) REFERENCES event_source (source)
) ENGINE = InnoDB;
