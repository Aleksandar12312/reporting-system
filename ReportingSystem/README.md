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
`recycler.RecyclerEvent`; keep both equal. `event_source.part` says which component of the machine an event is
about (for now `SCANNER` and `RECYCLER`; `NULL` = not assigned). It is only in the database, so it can be changed
with an `UPDATE` at any time. `event.source` has a foreign key to `event_source`, so a misspelled name is
refused. Per unit and day the generator sends about 10 customer transactions (`transactions-per-day`, a deposit is
about 4 events, a payout 2), 2 warnings/state changes (`status-events-per-day`) and the faults of its model and profile:
about 35 events per unit per day, ~620,000 events for the 90-day history. Command replies and coin/ticket events are
in the list but never generated. Only FAULT events count as problems (`stats.fault-sources`).

The answer key (which unit got which planted behaviour) is in the table `mock_unit_profile`.
`mock.planted.dirty-scanner` units have normal faults, but their scanner rejects 30% of the notes instead of 8%.
New profiles only appear after a re-seed (see below); an existing database keeps its units.

## Re-seeding from scratch

Re-run `schema.sql` and `schema_mock_key.sql`, then start the app again. `schema.sql` also drops the statistics
tables; the app creates and fills them again. To keep an existing database instead, run
`db/migrate_recycler_events.sql` (widens the text columns, adds `event_source`, deletes the old non-recycler events).
An existing database from before the column `event_source.part` needs `db/migrate_event_parts.sql` once (adds the
column and fills it for SCANNER and RECYCLER); the app tells you in the log when it is missing.
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

- `/api/summary`: `{computedAt, windowDays, unitCount, tiers, issues}`, for the dashboard cards and "Updated hh:mm"
  (`issues`: units with a problem per component)
- `/api/models`: one row per model from `model_stats`
- `/api/fault-kinds`: the fault kinds that are counted (`stats.fault-sources`)
- `/api/components`: the components that get their own message (`ranking.components`), in order, with their rule
  and resolved event names; a front end can build one tab per component from it

- `POST /api/units/{unitId}/issues/{component}/ack` with `{"by": "Marko", "note": "scanner cleaned"}`:
  mark one component of a unit (an id of `ranking.components`, e.g. `SCANNER`) as OK; answers with the unit as it is
  now (400 for an unknown component)
- `POST /api/units/{unitId}/issues/ack`: the same for every component that has a problem now
- `/api/units/{unitId}/issues/history?limit=50`: every "marked OK" of a unit, newest first

`limit` must be between 1 and 1000. A wrong value gives status 400 with the reason.
The two POST calls are the only calls that write; they write only to `unit_issue_ack`.

### Component messages (`issues`)

Every unit in the API has `issues`: one entry per component of `ranking.components` (now `SCANNER` and
`RECYCLER`, in that order), with `status` `OK`, `ACTIVE` (something is wrong) or `RECURRING` (wrong again within
`ranking.recurring-days` after it was marked OK). `IssueEngine` compares the unit with the median of its model:

- `metric: SHARE`: problems / work (scanner: `NOTE_REJECTED / READ_NOTE`) at least `ratio` x the median, with at
  least `min-work` work and `min-problems` problem events
- `metric: PER_DAY`: problem events per day (recycler: every FAULT event with `event_source.part = RECYCLER`, i.e.
  payout errors and note jams) at least `ratio` x the median, with at least `min-problems` in at least `min-days` days

The counts come from `unit_event_daily`. **A new component** (e.g. coins) = set `event_source.part` for its events,
add a block to `ranking.components` and restart: no new table, no new code, no rebuild (every event is already
counted). Every event name of the YAML must exist in `event_source`; a wrong name is reported in the log on start.

"Marked OK" is stored in `unit_issue_ack` (created by the app, `db/schema_issues.sql`, never touched by a statistics
rebuild). After it, only the days after that day count for that unit and component, so old data cannot bring the
message back; the model medians always use every unit's whole window.

The tier of the fault rate is raised to at least WATCH while a unit has a SCANNER or RECYCLER message that is
ACTIVE or RECURRING (OK and NO_BASELINE become WATCH; WATCH and CRITICAL stay). So such a unit is in
`/problematic`, in the WATCH count of `/summary` and in `?tier=WATCH`. Rate, ratio and scope stay those of the faults.
`topPart` and the part of `/faults` and the event lists are `event_source.part` (`OTHER` for a fault without one).
Marking the component as OK brings the tier of the fault rate back.

Tier and scope are decided by `RankingEngine` from the `ranking:` block of `application-dev.yml`
(`min-faults`, `watch-ratio`, `critical-ratio`, `part-min-faults`, `part-share`). The rate, the model median, the window and the
fault sources come from the statistics job (`stats:` block). The old Java scoring code is in `scoring_backup.zip`;
the same algorithm still exists as the SQL view in `db/unit_score.sql`.

Tests: `mvn test`.

## Statistics tables (package `stats`, Spring Batch)

The job `statsRefreshJob` keeps four tables up to date, so large amounts of data do not have to be counted again
for every question:

- `unit_event_daily`: events per unit, day and event name, for every event (faults, notes read and rejected,
  payouts ...). Faults, tier, the component messages and the fault lists are all read from it. Which component an
  event belongs to is joined from `event_source.part` when reading, so it is not stored here
- `unit_stats`: one row per unit (also a new unit without faults): faults and rate in the window, total faults, last fault day
- `model_stats`: one row per model: unit counts, faults, median and mean faults per day of the baseline units
- `stats_etl_state`: the id of the last event already counted

Window, baseline and fault sources come from the `stats:` block. The window is counted in whole days (from the first day of the window).

When it runs: once when the app is ready (after the mock seeder), then on `stats.refresh-cron`
(default 30 seconds after every 5-minute mock tick). Steps:

1. `prepareStatsStep`: if the tables are empty, or the event table was re-created (re-seed), everything is built
   again from the start; otherwise only the new events are read
2. `unitEventDailyStep`: the new events are added to `unit_event_daily`, `stats.slice-size` event ids per transaction
   (the counting and the new last event id are committed together, so a crash never counts an event twice)
3. `unitStatsStep`: reader / processor / writer, one `unit_stats` row per unit
4. `modelStatsStep`: `model_stats` calculated again (one row per model)

The app creates the tables itself (`db/schema_stats.sql`; it also drops the old `unit_fault_daily` and
`unit_scanner_daily`, which `unit_event_daily` replaced, and then builds the statistics once from the event table)
and Spring Boot creates the Spring Batch tables (`BATCH_...`).
Useful scripts: `db/stats_checks.sql` (read-only checks, last job runs), `db/stats_reset.sql` (forces a full rebuild).
Settings: the `stats:` block of `application-dev.yml`; `stats.enabled: false` switches the whole job off.

Assumption: events are only ever added (never changed or deleted) and written by one writer at a time (the mock generator).
