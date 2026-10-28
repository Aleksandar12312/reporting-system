package com.example.monitor.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;

import com.example.monitor.api.ComponentCatalog.ComponentDefinition;
import com.example.monitor.api.IssueEngine.Counts;
import com.example.monitor.api.IssueEngine.LastOk;
import com.example.monitor.api.IssueEngine.UnitWindow;

/**
 * SQL of the component messages (ranking.components) and of "marked as OK".
 * The counts come from unit_event_daily, in the window of unit_stats (whole days, as the statistics job
 * counts them). "Since OK" counts only the days after the day of the newest "marked OK" of that component.
 */
@Repository
public class IssueRepository {

    static final String SCHEMA_SCRIPT = "db/schema_issues.sql";

    private final JdbcTemplate jdbc;

    public IssueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates unit_issue_ack if it is missing; never removes data. */
    public void createTableIfMissing() {
        new ResourceDatabasePopulator(new ClassPathResource(SCHEMA_SCRIPT)).execute(jdbc.getDataSource());
    }

    public List<UnitWindow> loadUnitWindows() {
        return jdbc.query("""
                select us.unit_id, t.model_name, us.window_start, us.computed_at, us.exposure_days
                from unit_stats us
                join unit u on u.id = us.unit_id
                join unit_type t on t.id = u.unit_type_id
                """,
                (rs, rowNum) -> new UnitWindow(rs.getLong("unit_id"), rs.getString("model_name"),
                        rs.getObject("window_start", LocalDateTime.class),
                        rs.getObject("computed_at", LocalDateTime.class), rs.getDouble("exposure_days")));
    }

    /**
     * Work and problem events of one component per unit, in the window and since its newest "marked OK".
     * Units without any of its events are not in the list (they count as 0).
     */
    public List<Counts> loadCounts(ComponentDefinition component) {
        String work = placeholders(component.work());
        String problems = placeholders(component.problems());
        String all = placeholders(component.allSources());
        List<Object> args = new ArrayList<>();
        args.addAll(component.work());
        args.addAll(component.problems());
        args.addAll(component.work());
        args.addAll(component.problems());
        args.addAll(component.allSources());
        args.add(component.id());
        return jdbc.query("""
                select us.unit_id,
                       coalesce(sum(case when d.source in (%1$s) then d.event_count else 0 end), 0) as work_window,
                       coalesce(sum(case when d.source in (%2$s) then d.event_count else 0 end), 0) as problems_window,
                       coalesce(sum(case when d.source in (%1$s)
                                          and (a.last_ok is null or d.event_date > date(a.last_ok))
                                         then d.event_count else 0 end), 0) as work_since,
                       coalesce(sum(case when d.source in (%2$s)
                                          and (a.last_ok is null or d.event_date > date(a.last_ok))
                                         then d.event_count else 0 end), 0) as problems_since
                from unit_stats us
                join unit_event_daily d on d.unit_id = us.unit_id
                     and d.event_date >= date(us.window_start) and d.event_date <= date(us.computed_at)
                     and d.source in (%3$s)
                left join (select unit_id, max(acked_at) as last_ok
                           from unit_issue_ack where component = ? group by unit_id) a
                     on a.unit_id = us.unit_id
                group by us.unit_id
                """.formatted(work, problems, all),
                (rs, rowNum) -> new Counts(rs.getLong("unit_id"), rs.getInt("work_window"),
                        rs.getInt("problems_window"), rs.getInt("work_since"), rs.getInt("problems_since")),
                args.toArray());
    }

    /** "?, ?, ?" with one placeholder per name; "null" for an empty list (matches nothing, valid SQL). */
    private static String placeholders(List<String> names) {
        return names.isEmpty() ? "null" : names.stream().map(n -> "?").collect(Collectors.joining(", "));
    }

    /** The newest "marked OK" per unit and component. */
    public List<LastOk> loadLastOks() {
        return jdbc.query("""
                select a.unit_id, a.component, a.acked_at, a.acked_by, a.note
                from unit_issue_ack a
                join (select max(id) as max_id from unit_issue_ack group by unit_id, component) m on m.max_id = a.id
                """,
                (rs, rowNum) -> new LastOk(rs.getLong("unit_id"), rs.getString("component"),
                        rs.getObject("acked_at", LocalDateTime.class), rs.getString("acked_by"), rs.getString("note")));
    }

    public void insertAck(long unitId, String component, LocalDateTime at, String by, String note) {
        jdbc.update("insert into unit_issue_ack (unit_id, component, acked_at, acked_by, note) values (?, ?, ?, ?, ?)",
                unitId, component, at, by, note);
    }

    // ------------------------------------------------------------------ reset unit

    public void insertReset(long unitId, LocalDateTime at, String by, String note) {
        jdbc.update("insert into unit_reset (unit_id, reset_at, reset_by, note) values (?, ?, ?, ?)",
                unitId, at, by, note);
    }

    /**
     * Makes the unit_stats row of a reset unit what the statistics job will calculate for it: the window starts at
     * {@code countingFrom}, nothing counted yet. Without this the reset would only show after the next job run.
     * total_faults and last_fault_date stay (they are the history of the unit).
     */
    public void restartUnitStats(long unitId, LocalDateTime countingFrom, LocalDateTime now) {
        jdbc.update("""
                update unit_stats
                set window_start = ?, exposure_days = 0, faults_in_window = 0, rate_per_day = 0,
                    in_baseline = false, computed_at = ?
                where unit_id = ?
                """, countingFrom, now, unitId);
    }

    /** Every reset of one unit, newest first. */
    public List<UnitReset> loadResets(long unitId, int limit) {
        return jdbc.query("""
                select id, unit_id, reset_at, reset_by, note
                from unit_reset where unit_id = ? order by reset_at desc, id desc limit ?
                """,
                (rs, rowNum) -> {
                    LocalDateTime at = rs.getObject("reset_at", LocalDateTime.class);
                    return new UnitReset(rs.getLong("id"), rs.getLong("unit_id"), at, rs.getString("reset_by"),
                            rs.getString("note"), countingFromReset(at));
                },
                unitId, limit);
    }

    /** Start of the day after a reset: the same rule as "since OK" and as the statistics job. */
    static LocalDateTime countingFromReset(LocalDateTime resetAt) {
        return resetAt.toLocalDate().plusDays(1).atStartOfDay();
    }

    /** Every "marked OK" of one unit, newest first. */
    public List<IssueAck> loadHistory(long unitId, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(unitId);
        args.add(limit);
        return jdbc.query("""
                select id, unit_id, component, acked_at, acked_by, note
                from unit_issue_ack where unit_id = ? order by acked_at desc, id desc limit ?
                """,
                (rs, rowNum) -> new IssueAck(rs.getLong("id"), rs.getLong("unit_id"),
                        rs.getString("component"),
                        rs.getObject("acked_at", LocalDateTime.class), rs.getString("acked_by"), rs.getString("note")),
                args.toArray());
    }
}
