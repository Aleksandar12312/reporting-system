-- Upgrade of an EXISTING mock_lincstation database (MySQL 8): event_source gets the column part,
-- the component of the machine an event is about (SCANNER or RECYCLER, never NULL).
-- Run it once, before starting the new version of the application.
-- Not needed after a fresh schema.sql (it already has the column). If you also run migrate_recycler_events.sql,
-- run it before this script (it rewrites event_source and would clear the parts).
-- Already added the column as nullable with an earlier version of this script? Run
-- migrate_event_parts_not_null.sql instead.
--
-- The old statistics tables unit_fault_daily and unit_scanner_daily are dropped by the application itself
-- on its next start (db/schema_stats.sql); it then fills unit_event_daily from the event table once.
-- No event and no "marked OK" (unit_issue_ack) is removed.
USE mock_lincstation;

-- 1. The new column; every existing event starts as SCANNER, the default is removed again at the end
ALTER TABLE event_source ADD COLUMN part VARCHAR(20) NOT NULL DEFAULT 'SCANNER' AFTER description;

-- 2. Components of the events
-- Every event belongs to one of two components: RECYCLER (payout, floating, emptying, jams, the note float,
-- notes moved by the payout) or SCANNER (everything else: reading and stacking notes, cashbox, coin, fraud,
-- start-up and replies to host commands).
UPDATE event_source SET part = 'RECYCLER'
WHERE source IN ('ERROR_DURING_PAYOUT', 'INCOMPLETE_PAYOUT', 'INCOMPLETE_FLOAT', 'PAYOUT_OUT_OF_SERVICE', 'TIME_OUT',
                 'SAFE_NOTE_JAM', 'UNSAFE_NOTE_JAM', 'JAMMED', 'JAM_RECOVERY',
                 'DISPENSING', 'DISPENSED', 'HALTED', 'FLOATING', 'FLOATED', 'EMPTYING', 'EMPTIED',
                 'SMART_EMPTYING', 'SMART_EMPTIED', 'NOTE_STORED_IN_PAYOUT', 'NOTE_FLOAT_REMOVED', 'NOTE_FLOAT_ATTACHED',
                 'NOTE_TRANSFERED_TO_STACKER', 'NOTE_PAID_INTO_STACKER_AT_POWER-UP', 'NOTE_PAID_INTO_STORE_AT_POWER-UP',
                 'NOTE_DISPENSED_AT_POWER-UP', 'NOTE_HELD_IN_BEZEL', 'CASHBOX_PAID');

ALTER TABLE event_source ALTER COLUMN part DROP DEFAULT;

-- 3. Check: how many events per component, and every event name with its component
SELECT part, COUNT(*) AS events FROM event_source GROUP BY part;
SELECT part, category, source FROM event_source ORDER BY part, category, source;

-- Later, a new component is the same kind of UPDATE, e.g.
--   UPDATE event_source SET part = 'COIN' WHERE source LIKE 'COIN%';
-- plus a block in ranking.components of application-dev.yml, and a restart of the application.
