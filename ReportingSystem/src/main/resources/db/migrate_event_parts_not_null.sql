-- For a database where event_source.part already exists as a nullable column (first version of
-- migrate_event_parts.sql): events without a component become OTHER and the column can no longer be NULL.
-- New event_source rows get OTHER by themselves. Safe to run again.
USE mock_lincstation;

UPDATE event_source SET part = 'OTHER' WHERE part IS NULL;
ALTER TABLE event_source MODIFY COLUMN part VARCHAR(20) NOT NULL DEFAULT 'OTHER';

-- Check: how many events per component
SELECT part, COUNT(*) AS events FROM event_source GROUP BY part ORDER BY part = 'OTHER', part;
