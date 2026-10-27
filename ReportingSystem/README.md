# problematic-units

Spring Boot project with a mock machine-event generator (MySQL 8, Java 17+).

## Run

1. Create the tables: run `src/main/resources/db/schema.sql`, then `src/main/resources/db/schema_mock_key.sql`
   (this creates the `mock_lincstation` database too).
2. Put your MySQL password in `src/main/resources/application-dev.yml`.
3. `mvn test` to check the build, then `mvn spring-boot:run`.
   The `dev` profile is active by default (see `application.yml`).

On the first start the generator creates 200 units and 90 days of history. After that it adds events every
5 minutes. If the app was stopped, the next run fills the missed 5-minute ticks (up to `backfill-days`).

Every event comes from the bill recycler of the machine (`type = PLACEHOLDER`, `placeholder = RC`), and its
`source` is a name from the generic recycler event list (58 events, codes 176-250). The list is in the table
`event_source` (with a category: FAULT, WARNING, STATUS, OPERATION, COMMAND) and in the Java enum
`recycler.RecyclerEvent`; keep both equal. `event.source` has a foreign key to `event_source`, so a misspelled name is
refused. Per unit and day the generator sends about 10 customer transactions (`transactions-per-day`, a deposit is
about 4 events, a payout 2), 2 warnings/state changes (`status-events-per-day`) and the faults of its model and profile:
about 35 events per unit per day, ~620,000 events for the 90-day history. Command replies and coin/ticket events are
in the list but never generated. Only FAULT events count as problems (`stats.fault-sources`).

The answer key (which unit got which planted behaviour) is in the table `mock_unit_profile`.

## Re-seeding from scratch

Re-run `schema.sql` and `schema_mock_key.sql`, then start the app again. `schema.sql` also drops the statistics
tables; the app creates and fills them again. To keep an existing database instead, run
`db/migrate_recycler_events.sql` (widens the text columns, adds `event_source`, deletes the old non-recycler events).
Set `mock.enabled: false` to switch the whole generator off.

## REST API (package `api`)

The web server runs on port 8080. All calls are GET and read-only, JSON, errors are `{"error": "..."}`.
The numbers come from the statistics tables (so they are as fresh as the last job run, up to 5 minutes old);
the event lists are read live from `event`.

- `/api/units/scores`: every unit, worst first
- `/api/units/problematic`: WATCH and CRITICAL units; optional `?tier=CRITICAL` or `?tier=WATCH`
- `/api/units/worst?limit=10`: the worst units, CRITICAL first
- `/api/units/top-by-fault?fault=JAMMED&limit=10`: units with the most faults of one kind (`stats.fault-sources`)
- `/api/units/{unitId}`: one unit (status 404 if unknown)
- `/api/units/{unitId}/faults`: faults of one unit by component and fault name
- `/api/units/{unitId}/fault-events?limit=100`: newest fault events of one unit
- `/api/units/{unitId}/events?limit=100`: newest events of one unit, of every kind

- `/api/summary`: `{computedAt, windowDays, unitCount, tiers}`, for the dashboard cards and "Updated hh:mm"
- `/api/models`: one row per model from `model_stats`
- `/api/fault-kinds`: the fault kinds that are counted (`stats.fault-sources`)

`limit` must be between 1 and 1000. A wrong value gives status 400 with the reason.

Tier and scope are decided by `RankingEngine` from the `ranking:` block of `application-dev.yml`
(`min-faults`, `watch-ratio`, `critical-ratio`, `part-min-faults`, `part-share`). The rate, the model median, the window and the
fault sources come from the statistics job (`stats:` block). The old Java scoring code is in `scoring_backup.zip`;
the same algorithm still exists as the SQL view in `db/unit_score.sql`.

Tests: `mvn test`.

## Statistics tables (package `stats`, Spring Batch)

The job `statsRefreshJob` keeps four tables up to date, so large amounts of data do not have to be counted again
for every question:

- `unit_fault_daily`: faults per unit, day, component and fault name
- `unit_stats`: one row per unit (also a new unit without faults): faults and rate in the window, total faults, last fault day
- `model_stats`: one row per model: unit counts, faults, median and mean faults per day of the baseline units
- `stats_etl_state`: the id of the last event already counted

Window, baseline and fault sources come from the `stats:` block. The window is counted in whole days (from the first day of the window).

When it runs: once when the app is ready (after the mock seeder), then on `stats.refresh-cron`
(default 30 seconds after every 5-minute mock tick). Steps:

1. `prepareStatsStep`: if the tables are empty, or the event table was re-created (re-seed), everything is built
   again from the start; otherwise only the new events are read
2. `unitFaultDailyStep`: the new fault events are added to `unit_fault_daily`, `stats.slice-size` event ids per transaction
   (the counting and the new last event id are committed together, so a crash never counts an event twice)
3. `unitStatsStep`: reader / processor / writer, one `unit_stats` row per unit
4. `modelStatsStep`: `model_stats` calculated again (one row per model)

The app creates the tables itself (`db/schema_stats.sql`) and Spring Boot creates the Spring Batch tables (`BATCH_...`).
Useful scripts: `db/stats_checks.sql` (read-only checks, last job runs), `db/stats_reset.sql` (forces a full rebuild).
Settings: the `stats:` block of `application-dev.yml`; `stats.enabled: false` switches the whole job off.

Assumption: events are only ever added (never changed or deleted) and written by one writer at a time (the mock generator).
