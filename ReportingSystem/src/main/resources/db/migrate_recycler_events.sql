-- Upgrade of an EXISTING mock_lincstation database to the recycler events (MySQL 8).
-- For a fresh start you do not need this: run schema.sql and schema_mock_key.sql again instead (recommended,
-- because the mock generator now writes recycler events, and the old events no longer fit).
--
-- Run the steps in order. Steps 1-3 change no data. Step 4 deletes the old (non-recycler) events.
USE mock_lincstation;

-- 1. Wider text columns: the longest recycler event name has 34 characters, more than VARCHAR(30)
ALTER TABLE event
    MODIFY type         VARCHAR(40) NULL,
    MODIFY placeholder  VARCHAR(40) NULL,
    MODIFY source       VARCHAR(40) NULL;

-- Same for the statistics tables, if they exist already (skip these two lines if they do not)
ALTER TABLE unit_fault_daily
    MODIFY part   VARCHAR(40) NOT NULL,
    MODIFY fault  VARCHAR(40) NOT NULL;

-- 2. The list of valid event names
CREATE TABLE IF NOT EXISTS event_source (
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

-- REPLACE: running the script twice gives the same 58 rows
REPLACE INTO event_source (code, source, category, description) VALUES
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

-- 3. Which event names in the event table are NOT recycler events (they block the foreign key of step 5)
SELECT e.source, COUNT(*) AS events
FROM event e
LEFT JOIN event_source s ON s.source = e.source
WHERE e.source IS NOT NULL AND s.source IS NULL
GROUP BY e.source
ORDER BY events DESC;

-- 4. Delete those old events (DOOR_OPEN, RAIL_STOP, ...). The statistics notice it and are built again.
--    Workbench refuses a DELETE without a key in WHERE ("safe updates"), so it is switched off for this one statement.
SET SQL_SAFE_UPDATES = 0;
DELETE e FROM event e
LEFT JOIN event_source s ON s.source = e.source
WHERE e.source IS NOT NULL AND s.source IS NULL;
SET SQL_SAFE_UPDATES = 1;

-- 5. From now on only names from event_source can be stored
ALTER TABLE event
    ADD KEY idx_event_source (source),
    ADD CONSTRAINT fk_event_source FOREIGN KEY (source) REFERENCES event_source (source);
