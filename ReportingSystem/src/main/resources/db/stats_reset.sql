-- Forces a complete rebuild of the statistics (MySQL 8). Normally not needed: the job notices a re-seed itself.
-- After this, the next run of the job (at the latest 5 minutes later, or at the next start) counts every event again.
USE mock_lincstation;

TRUNCATE TABLE unit_fault_daily;
TRUNCATE TABLE unit_scanner_daily;
TRUNCATE TABLE unit_stats;
TRUNCATE TABLE model_stats;
TRUNCATE TABLE stats_etl_state;
