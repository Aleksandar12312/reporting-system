package com.example.monitor.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Repository;

import com.example.monitor.api.IssueEngine.LastOk;
import com.example.monitor.api.IssueEngine.RecyclerCounts;
import com.example.monitor.api.IssueEngine.ScannerCounts;
import com.example.monitor.api.IssueEngine.UnitWindow;

/**
 * SQL of the component messages (scanner, recycler) and of "marked as OK".
 * The counts come from the statistics tables, in the window of unit_stats (whole days, as the statistics job
 * counts them). "Since OK" counts only the days after the day of the newest "marked OK".
 */
@Repository
public class IssueRepository {

    static final String SCHEMA_SCRIPT = "db/schema_issues.sql";

    private final JdbcTemplate jdbc;
    private final RankingProperties ranking;

    public IssueRepository(JdbcTemplate jdbc, RankingProperties ranking) {
        this.jdbc = jdbc;
        this.ranking = ranking;
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

    public List<ScannerCounts> loadScannerCounts() {
        return jdbc.query("""
                select us.unit_id,
                       coalesce(sum(s.notes_read), 0) as reads_window,
                       coalesce(sum(s.notes_rejected), 0) as rejected_window,
                       coalesce(sum(case when a.last_ok is null or s.event_date > date(a.last_ok)
                                         then s.notes_read else 0 end), 0) as reads_since,
                       coalesce(sum(case when a.last_ok is null or s.event_date > date(a.last_ok)
                                         then s.notes_rejected else 0 end), 0) as rejected_since
                from unit_stats us
                join unit_scanner_daily s on s.unit_id = us.unit_id
                     and s.event_date >= date(us.window_start) and s.event_date <= date(us.computed_at)
                left join (select unit_id, max(acked_at) as last_ok
                           from unit_issue_ack where component = 'SCANNER' group by unit_id) a
                     on a.unit_id = us.unit_id
                group by us.unit_id
                """,
                (rs, rowNum) -> new ScannerCounts(rs.getLong("unit_id"), rs.getInt("reads_window"),
                        rs.getInt("rejected_window"), rs.getInt("reads_since"), rs.getInt("rejected_since")));
    }

    public List<RecyclerCounts> loadRecyclerCounts() {
        List<String> faults = ranking.recycler().faults();
        String placeholders = faults.stream().map(f -> "?").collect(Collectors.joining(", "));
        return jdbc.query("""
                select us.unit_id,
                       coalesce(sum(d.faults), 0) as faults_window,
                       coalesce(sum(case when a.last_ok is null or d.event_date > date(a.last_ok)
                                         then d.faults else 0 end), 0) as faults_since
                from unit_stats us
                join unit_fault_daily d on d.unit_id = us.unit_id
                     and d.event_date >= date(us.window_start) and d.event_date <= date(us.computed_at)
                     and d.fault in (%s)
                left join (select unit_id, max(acked_at) as last_ok
                           from unit_issue_ack where component = 'RECYCLER' group by unit_id) a
                     on a.unit_id = us.unit_id
                group by us.unit_id
                """.formatted(placeholders),
                (rs, rowNum) -> new RecyclerCounts(rs.getLong("unit_id"), rs.getInt("faults_window"),
                        rs.getInt("faults_since")),
                faults.toArray());
    }

    /** The newest "marked OK" per unit and component. */
    public List<LastOk> loadLastOks() {
        return jdbc.query("""
                select a.unit_id, a.component, a.acked_at, a.acked_by, a.note
                from unit_issue_ack a
                join (select max(id) as max_id from unit_issue_ack group by unit_id, component) m on m.max_id = a.id
                """,
                (rs, rowNum) -> new LastOk(rs.getLong("unit_id"), IssueComponent.valueOf(rs.getString("component")),
                        rs.getObject("acked_at", LocalDateTime.class), rs.getString("acked_by"), rs.getString("note")));
    }

    public void insertAck(long unitId, IssueComponent component, LocalDateTime at, String by, String note) {
        jdbc.update("insert into unit_issue_ack (unit_id, component, acked_at, acked_by, note) values (?, ?, ?, ?, ?)",
                unitId, component.name(), at, by, note);
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
                        IssueComponent.valueOf(rs.getString("component")),
                        rs.getObject("acked_at", LocalDateTime.class), rs.getString("acked_by"), rs.getString("note")),
                args.toArray());
    }
}
