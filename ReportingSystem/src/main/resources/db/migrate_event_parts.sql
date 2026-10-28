-- Upgrade of an EXISTING mock_lincstation database (MySQL 8): event_source gets the column part,
-- the component of the machine an event is about. Run it once, before starting the new version of the application.
-- Not needed after a fresh schema.sql (it already has the column). If you also run migrate_recycler_events.sql,
-- run it before this script (it rewrites event_source and would clear the parts).
--
-- The old statistics tables unit_fault_daily and unit_scanner_daily are dropped by the application itself
-- on its next start (db/schema_stats.sql); it then fills unit_event_daily from the event table once.
-- No event and no "marked OK" (unit_issue_ack) is removed.
USE mock_lincstation;

-- 1. The new column (NULL = not assigned to a component)
ALTER TABLE event_source ADD COLUMN part VARCHAR(20) NULL AFTER description;

-- 2. Components of the events. For now only the scanner and the recycler;
-- the coin, cashbox and system events stay NULL until they get a component of their own.
UPDATE event_source SET part = 'SCANNER'
WHERE source IN ('READ_NOTE', 'NOTE_REJECTING', 'NOTE_REJECTED', 'CREDIT_NOTE');

UPDATE event_source SET part = 'RECYCLER'
WHERE source IN ('ERROR_DURING_PAYOUT', 'INCOMPLETE_PAYOUT', 'INCOMPLETE_FLOAT', 'PAYOUT_OUT_OF_SERVICE', 'TIME_OUT',
                 'SAFE_NOTE_JAM', 'UNSAFE_NOTE_JAM', 'JAMMED', 'JAM_RECOVERY',
                 'DISPENSING', 'DISPENSED', 'HALTED', 'FLOATING', 'FLOATED', 'EMPTYING', 'EMPTIED',
                 'SMART_EMPTYING', 'SMART_EMPTIED', 'NOTE_STORED_IN_PAYOUT', 'NOTE_FLOAT_REMOVED', 'NOTE_FLOAT_ATTACHED');

-- 3. Check: every event name with its component
SELECT part, category, source FROM event_source ORDER BY part IS NULL, part, category, source;

-- Later, a new component is the same kind of UPDATE, e.g.
--   UPDATE event_source SET part = 'COIN' WHERE source LIKE 'COIN%';
-- plus a block in ranking.components of application-dev.yml, and a restart of the application.
