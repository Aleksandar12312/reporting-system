package com.example.monitor.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.monitor.api.RankingEngine.PartCount;
import com.example.monitor.api.RankingEngine.ScoreRow;
import com.example.monitor.stats.StatsProperties;

/**
 * Read-only SQL of the REST API. The numbers come from the statistics tables (unit_stats, model_stats,
 * unit_fault_daily), which the statistics job keeps up to date; only the event lists read the event table.
 *
 * The window of a unit is the one the job used: from the day of unit_stats.window_start to the day of
 * unit_stats.computed_at. So the counts here always match the faults_in_window of unit_stats.
 */
@Repository
public class UnitReadRepository {

    private final JdbcTemplate jdbc;
    private final StatsProperties stats;

    public UnitReadRepository(JdbcTemplate jdbc, StatsProperties stats) {
        this.jdbc = jdbc;
        this.stats = stats;
    }

    /** Every unit with its window numbers and the median of its model. */
    public List<ScoreRow> loadScoreRows() {
        return loadScoreRows(null);
    }

    /** Like {@link #loadScoreRows()}, but only one unit when unitId is given (empty list if it is unknown). */
    public List<ScoreRow> loadScoreRows(Long unitId) {
        String where = unitId == null ? "" : " where us.unit_id = ?";
        Object[] args = unitId == null ? new Object[0] : new Object[] {unitId};
        return jdbc.query("""
                select us.unit_id, u.serial_number, t.model_name, us.faults_in_window, us.exposure_days,
                       us.rate_per_day, ms.median_rate_per_day
                from unit_stats us
                join unit u on u.id = us.unit_id
                join unit_type t on t.id = u.unit_type_id
                left join model_stats ms on ms.unit_type_id = u.unit_type_id
                """ + where,
                (rs, rowNum) -> new ScoreRow(
                        rs.getLong("unit_id"), rs.getString("serial_number"), rs.getString("model_name"),
                        rs.getInt("faults_in_window"), rs.getDouble("exposure_days"), rs.getDouble("rate_per_day"),
                        rs.getObject("median_rate_per_day", Double.class)),
                args);
    }

    /** Faults per unit and component in the unit's window. */
    public List<PartCount> loadPartCounts() {
        return loadPartCounts(null);
    }

    /** Like {@link #loadPartCounts()}, but only one unit when unitId is given. */
    public List<PartCount> loadPartCounts(Long unitId) {
        String unitFilter = unitId == null ? "" : " and d.unit_id = ?";
        Object[] args = unitId == null ? new Object[0] : new Object[] {unitId};
        return jdbc.query("""
                select d.unit_id, d.part, sum(d.faults) as fault_count
                from unit_fault_daily d
                join unit_stats us on us.unit_id = d.unit_id
                where d.event_date >= date(us.window_start) and d.event_date <= date(us.computed_at)
                """ + unitFilter + """
                 group by d.unit_id, d.part
                """,
                (rs, rowNum) -> new PartCount(rs.getLong("unit_id"), rs.getString("part"), rs.getInt("fault_count")),
                args);
    }

    /** One row per model. */
    public List<ModelStatsRow> loadModelStats() {
        return jdbc.query("""
                select unit_type_id, model_name, window_days, unit_count, baseline_unit_count, faults_in_window,
                       median_rate_per_day, mean_rate_per_day, computed_at
                from model_stats
                order by model_name
                """,
                (rs, rowNum) -> new ModelStatsRow(
                        rs.getLong("unit_type_id"), rs.getString("model_name"), rs.getInt("window_days"),
                        rs.getInt("unit_count"), rs.getInt("baseline_unit_count"), rs.getLong("faults_in_window"),
                        rs.getObject("median_rate_per_day", Double.class),
                        rs.getObject("mean_rate_per_day", Double.class),
                        rs.getObject("computed_at", LocalDateTime.class)));
    }

    /** When unit_stats was last calculated (UTC); null while the table is empty. */
    public LocalDateTime loadLastComputedAt() {
        return jdbc.queryForObject("select max(computed_at) from unit_stats", LocalDateTime.class);
    }

    /** Faults of one unit by component and fault name in its window, most frequent first. */
    public List<FaultBreakdown> loadBreakdown(long unitId) {
        List<FaultRow> rows = jdbc.query("""
                select d.part, d.fault, sum(d.faults) as fault_count
                from unit_fault_daily d
                join unit_stats us on us.unit_id = d.unit_id
                where d.unit_id = ?
                  and d.event_date >= date(us.window_start) and d.event_date <= date(us.computed_at)
                group by d.part, d.fault
                order by fault_count desc, d.part, d.fault
                """,
                (rs, rowNum) -> new FaultRow(rs.getString("part"), rs.getString("fault"), rs.getInt("fault_count")),
                unitId);
        return withPercentages(unitId, rows);
    }

    /** Units with the most faults of one kind in their window, most first; units without that fault are left out. */
    public List<FaultRanking> loadFaultRanking(String fault, int limit) {
        return jdbc.query("""
                select u.id as unit_id, u.serial_number, t.model_name, us.exposure_days, sum(d.faults) as fault_count
                from unit_fault_daily d
                join unit_stats us on us.unit_id = d.unit_id
                join unit u on u.id = d.unit_id
                join unit_type t on t.id = u.unit_type_id
                where d.fault = ?
                  and d.event_date >= date(us.window_start) and d.event_date <= date(us.computed_at)
                group by u.id, u.serial_number, t.model_name, us.exposure_days
                order by fault_count desc, u.id
                limit ?
                """,
                (rs, rowNum) -> {
                    int faults = rs.getInt("fault_count");
                    double exposureDays = rs.getDouble("exposure_days");
                    double rate = exposureDays > 0 ? faults / exposureDays : 0;
                    return new FaultRanking(rs.getLong("unit_id"), rs.getString("serial_number"),
                            rs.getString("model_name"), fault, faults, exposureDays, rate);
                },
                fault, limit);
    }

    /**
     * Newest events of one unit, over its whole history (not only the window).
     * With faultsOnly only the configured fault sources are returned.
     */
    public List<UnitEvent> loadEvents(long unitId, boolean faultsOnly, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(unitId);
        String sourceFilter = "";
        if (faultsOnly) {
            sourceFilter = " and source in (" + sourcePlaceholders() + ")";
            args.addAll(stats.faultSources());
        }
        args.add(limit);
        String sql = "select id, unit_id, event_time, type, placeholder, source from event where unit_id = ?"
                + sourceFilter + " order by event_time desc, id desc limit ?";
        return jdbc.query(sql, (rs, rowNum) -> new UnitEvent(
                rs.getLong("id"), rs.getLong("unit_id"), rs.getObject("event_time", LocalDateTime.class),
                rs.getString("type"), rs.getString("placeholder"), rs.getString("source")), args.toArray());
    }

    /** One line of the breakdown query, before the percentages are added. */
    record FaultRow(String part, String fault, int faults) {
    }

    /** Adds each row's share of all the unit's faults, in percent with one decimal. */
    static List<FaultBreakdown> withPercentages(long unitId, List<FaultRow> rows) {
        int total = rows.stream().mapToInt(FaultRow::faults).sum();
        if (total == 0) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(r -> new FaultBreakdown(unitId, r.part(), r.fault(), r.faults(),
                        Math.round(1000.0 * r.faults() / total) / 10.0))
                .toList();
    }

    /** "?, ?, ?" with one placeholder per configured fault source. */
    private String sourcePlaceholders() {
        return stats.faultSources().stream().map(s -> "?").collect(Collectors.joining(", "));
    }
}
