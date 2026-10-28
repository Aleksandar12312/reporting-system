-- Deletes ONE unit and everything that belongs to it (MySQL 8). Set the id on the first line, run the whole script.
-- Order matters: event has a foreign key to unit, so the events go first and the unit last.
-- The statistics tables have no foreign keys, so they are cleaned by hand.
-- Not touched: unit_type, location, address (they can be shared with other units) and model_stats
-- (it is recalculated by the next statistics job run, so unit_count of the model is old until then).
USE mock_lincstation;

SET @unit_id = 123;                      -- <-- the id of the unit to delete

SET SQL_SAFE_UPDATES = 0;                -- Workbench safe update mode (error 1175), for this session only

-- Look first: what will be deleted
SELECT @unit_id AS unit_id,
       (SELECT COUNT(*) FROM unit WHERE id = @unit_id)                 AS unit,
       (SELECT COUNT(*) FROM event WHERE unit_id = @unit_id)           AS events,
       (SELECT COUNT(*) FROM unit_event_daily WHERE unit_id = @unit_id) AS daily_rows,
       (SELECT COUNT(*) FROM unit_issue_ack WHERE unit_id = @unit_id)   AS marked_ok;

START TRANSACTION;
DELETE FROM unit_issue_ack   WHERE unit_id = @unit_id;
DELETE FROM unit_reset       WHERE unit_id = @unit_id;
DELETE FROM unit_event_daily WHERE unit_id = @unit_id;
DELETE FROM unit_stats       WHERE unit_id = @unit_id;
DELETE FROM mock_unit_profile WHERE unit_id = @unit_id;   -- only exists with the mock data (schema_mock_key.sql); remove this line if the table is missing
DELETE FROM event            WHERE unit_id = @unit_id;    -- can take a while for a unit with many events
DELETE FROM unit             WHERE id = @unit_id;
COMMIT;

SET SQL_SAFE_UPDATES = 1;
