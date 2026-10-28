-- For a database where event_source.part already exists as a nullable column (first version of
-- migrate_event_parts.sql): every event gets SCANNER or RECYCLER and the column can no longer be NULL.
-- Only events without a component (NULL or OTHER) are changed, so it is safe to run again.
USE mock_lincstation;

UPDATE event_source SET part = 'RECYCLER'
WHERE (part IS NULL OR part = 'OTHER') AND source IN ('ERROR_DURING_PAYOUT', 'INCOMPLETE_PAYOUT', 'INCOMPLETE_FLOAT', 'PAYOUT_OUT_OF_SERVICE', 'TIME_OUT',
                 'SAFE_NOTE_JAM', 'UNSAFE_NOTE_JAM', 'JAMMED', 'JAM_RECOVERY',
                 'DISPENSING', 'DISPENSED', 'HALTED', 'FLOATING', 'FLOATED', 'EMPTYING', 'EMPTIED',
                 'SMART_EMPTYING', 'SMART_EMPTIED', 'NOTE_STORED_IN_PAYOUT', 'NOTE_FLOAT_REMOVED', 'NOTE_FLOAT_ATTACHED',
                 'NOTE_TRANSFERED_TO_STACKER', 'NOTE_PAID_INTO_STACKER_AT_POWER-UP', 'NOTE_PAID_INTO_STORE_AT_POWER-UP',
                 'NOTE_DISPENSED_AT_POWER-UP', 'NOTE_HELD_IN_BEZEL', 'CASHBOX_PAID');

UPDATE event_source SET part = 'SCANNER' WHERE part IS NULL OR part = 'OTHER';

ALTER TABLE event_source MODIFY COLUMN part VARCHAR(20) NOT NULL;

-- Check: how many events per component
SELECT part, COUNT(*) AS events FROM event_source GROUP BY part;
