package com.example.monitor.stats;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;


/**
 * All SQL of the statistics ETL. The tables are created by db/schema_stats.sql.
 *
 * <ul>
 *   <li>unit_fault_daily: faults per unit, day, component and fault name</li>
 *   <li>unit_stats: one row per unit (faults and rate in the window)</li>
 *   <li>model_stats: one row per model (median and mean rate)</li>
 *   <li>stats_etl_state: one row, the id of the last event already counted</li>
 * </ul>
 */
@Repository
@ConditionalOnProperty(prefix = "stats", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StatsRepository {

    static final String SCHEMA_SCRIPT = "db/schema_stats.sql";
    private static final String UNKNOWN_PART = "UNKNOWN";

    private final JdbcTemplate jdbc;
    private final List<String> faultSources;
    private final String sourcePlaceholders;

    public StatsRepository(JdbcTemplate jdbc, StatsProperties props) {
        this.jdbc = jdbc;
        this.faultSources = props.faultSources();
        this.sourcePlaceholders = String.join(", ", Collections.nCopies(faultSources.size(), "?"));
    }

    // ------------------------------------------------------------------ tables

    /**
     * Creates the four tables if they are missing. A model_stats table from the first version of the script
     * (without mean_rate_per_day) is dropped and created again; it holds only derived numbers.
     */
    public void createTablesIfMissing() {
        runSchemaScript();
        Integer meanColumns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'model_stats' and column_name = 'mean_rate_per_day'
                """, Integer.class);
        if (meanColumns == null || meanColumns == 0) {
            jdbc.execute("drop table model_stats");
            runSchemaScript();
        }
    }

    private void runSchemaScript() {
        new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_SCRIPT)).execute(jdbc.getDataSource());
    }

    // ------------------------------------------------------------------ state and checks

    /** The state row, locked until the end of the transaction. Empty on the very first run. */
    public Optional<EtlState> loadState() {
        return jdbc.query("""
                select last_event_id, check_event_id, check_unit_id, check_event_time
                from stats_etl_state where id = 1 for update
                """,
                (rs, rowNum) -> new EtlState(
                        rs.getLong("last_event_id"),
                        rs.getObject("check_event_id", Long.class),
                        rs.getObject("check_unit_id", Long.class),
                        rs.getObject("check_event_time", LocalDateTime.class)))
                .stream().findFirst();
    }

    /** Remembers that every event up to {@code lastEventId} is counted, together with a check event. */
    public void saveState(long lastEventId, LocalDateTime now) {
        List<Object[]> check = jdbc.query(
                "select id, unit_id, event_time from event where id <= ? order by id desc limit 1",
                (rs, rowNum) -> new Object[] {
                        rs.getLong("id"), rs.getLong("unit_id"), rs.getObject("event_time", LocalDateTime.class)},
                lastEventId);
        Object[] c = check.isEmpty() ? new Object[] {null, null, null} : check.get(0);
        jdbc.update("""
                insert into stats_etl_state (id, last_event_id, check_event_id, check_unit_id, check_event_time, updated_at)
                values (1, ?, ?, ?, ?, ?)
                on duplicate key update last_event_id = values(last_event_id), check_event_id = values(check_event_id),
                    check_unit_id = values(check_unit_id), check_event_time = values(check_event_time),
                    updated_at = values(updated_at)
                """, lastEventId, c[0], c[1], c[2], now);
    }

    /** The highest event id that is in the table right now (0 when the table is empty). */
    public long maxEventId() {
        Long max = jdbc.queryForObject("select coalesce(max(id), 0) from event", Long.class);
        return max == null ? 0 : max;
    }

    /** True when the check event of the state is still in the event table, unchanged. */
    public boolean checkEventIsUnchanged(EtlState state) {
        Integer found = jdbc.queryForObject(
                "select count(*) from event where id = ? and unit_id = ? and event_time = ?",
                Integer.class, state.checkEventId(), state.checkUnitId(), state.checkEventTime());
        return found != null && found > 0;
    }

    public boolean dailyTableIsEmpty() {
        Integer any = jdbc.queryForObject("select exists(select 1 from unit_fault_daily)", Integer.class);
        return any == null || any == 0;
    }

    /** True when at least one fault event has an id up to {@code eventId}. */
    public boolean faultEventsExistUpTo(long eventId) {
        Integer any = jdbc.queryForObject(
                "select exists(select 1 from event where id <= ? and source in (" + sourcePlaceholders + "))",
                Integer.class, withSources(eventId));
        return any != null && any > 0;
    }

    /**
     * Empties the statistics and forgets the state, so the next steps count every event again.
     * DELETE instead of TRUNCATE on purpose: TRUNCATE would commit the transaction in the middle of the step.
     */
    public void deleteAllStats() {
        jdbc.update("delete from unit_fault_daily");
        jdbc.update("delete from unit_stats");
        jdbc.update("delete from model_stats");
        jdbc.update("delete from stats_etl_state");
    }

    /** Removes unit_stats rows of units that no longer exist. Returns how many were removed. */
    public int deleteStatsOfRemovedUnits() {
        return jdbc.update("delete s from unit_stats s left join unit u on u.id = s.unit_id where u.id is null");
    }

    // ------------------------------------------------------------------ unit_fault_daily

    /**
     * Extract, transform and load in one statement: the fault events with an id in (fromId, toId] are grouped by
     * unit, day, component and fault name and added to unit_fault_daily (a new row, or added to the existing one).
     *
     * @return rows inserted or changed, as MySQL reports them (a changed row counts as 2)
     */
    public int addFaultsToDaily(long fromId, long toId) {
        String sql = """
                insert into unit_fault_daily (unit_id, event_date, part, fault, faults)
                select n.unit_id, n.event_date, n.part, n.fault, n.faults
                from (
                    select e.unit_id, date(e.event_time) as event_date,
                           coalesce(e.placeholder, '%s') as part, e.source as fault, count(*) as faults
                    from event e
                    where e.id > ? and e.id <= ? and e.source in (%s)
                    group by e.unit_id, date(e.event_time), coalesce(e.placeholder, '%s'), e.source
                ) n
                on duplicate key update faults = unit_fault_daily.faults + n.faults
                """.formatted(UNKNOWN_PART, sourcePlaceholders, UNKNOWN_PART);
        return jdbc.update(sql, withSources(fromId, toId));
    }

    // ------------------------------------------------------------------ unit_stats

    /**
     * Reader query of the unit_stats step: every unit with its faults from unit_fault_daily.
     * Parameters: 1 = start of the longest window (now - window-days), 2 = today.
     * A unit younger than the window counts from its creation day.
     */
    public static final String UNIT_TOTALS_SQL = """
            select u.id as unit_id, u.unit_type_id, u.creation_date,
                   coalesce(sum(case when d.event_date >= date(greatest(?, u.creation_date)) and d.event_date <= ?
                                     then d.faults else 0 end), 0) as faults_in_window,
                   coalesce(sum(d.faults), 0) as total_faults,
                   max(d.event_date) as last_fault_date
            from unit u
            left join unit_fault_daily d on d.unit_id = u.id
            group by u.id, u.unit_type_id, u.creation_date
            order by u.id
            """;

    /** Inserts or replaces the rows of the given units. */
    public void saveUnitStats(List<? extends UnitStats> rows) {
        jdbc.batchUpdate("""
                insert into unit_stats (unit_id, unit_type_id, window_start, exposure_days, faults_in_window,
                                        rate_per_day, in_baseline, total_faults, last_fault_date, computed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update unit_type_id = values(unit_type_id), window_start = values(window_start),
                    exposure_days = values(exposure_days), faults_in_window = values(faults_in_window),
                    rate_per_day = values(rate_per_day), in_baseline = values(in_baseline),
                    total_faults = values(total_faults), last_fault_date = values(last_fault_date),
                    computed_at = values(computed_at)
                """,
                rows.stream().map(r -> new Object[] {
                        r.unitId(), r.unitTypeId(), r.windowStart(), r.exposureDays(), r.faultsInWindow(),
                        r.ratePerDay(), r.inBaseline(), r.totalFaults(), r.lastFaultDate(), r.computedAt()})
                        .toList());
    }

    // ------------------------------------------------------------------ model_stats

    /** A model as listed in unit_type. */
    public record Model(long id, String name) {
    }

    /** What model_stats needs of one unit_stats row. */
    public record UnitRate(long unitTypeId, double ratePerDay, boolean inBaseline, int faultsInWindow) {
    }

    public List<Model> loadModels() {
        return jdbc.query("select id, model_name from unit_type order by id",
                (rs, rowNum) -> new Model(rs.getLong("id"), rs.getString("model_name")));
    }

    public List<UnitRate> loadUnitRates() {
        return jdbc.query("select unit_type_id, rate_per_day, in_baseline, faults_in_window from unit_stats",
                (rs, rowNum) -> new UnitRate(rs.getLong("unit_type_id"), rs.getDouble("rate_per_day"),
                        rs.getBoolean("in_baseline"), rs.getInt("faults_in_window")));
    }

    /** Replaces the whole model_stats table (one transaction, so a reader never sees it half full). */
    public void replaceModelStats(List<ModelStats> rows) {
        jdbc.update("delete from model_stats");
        jdbc.batchUpdate("""
                insert into model_stats (unit_type_id, model_name, window_days, unit_count, baseline_unit_count,
                                         faults_in_window, median_rate_per_day, mean_rate_per_day, computed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows.stream().map(r -> new Object[] {
                        r.unitTypeId(), r.modelName(), r.windowDays(), r.unitCount(), r.baselineUnitCount(),
                        r.faultsInWindow(), r.medianRatePerDay(), r.meanRatePerDay(), r.computedAt()})
                        .toList());
    }

    // ------------------------------------------------------------------ helpers

    /** The given parameters followed by the fault sources (for the "source in (?, ?, ...)" part). */
    private Object[] withSources(Object... first) {
        Object[] params = new Object[first.length + faultSources.size()];
        System.arraycopy(first, 0, params, 0, first.length);
        for (int i = 0; i < faultSources.size(); i++) {
            params[first.length + i] = faultSources.get(i);
        }
        return params;
    }
}
