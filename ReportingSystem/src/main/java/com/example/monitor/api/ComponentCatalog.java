package com.example.monitor.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.monitor.api.RankingProperties.ComponentRule;

/**
 * The components of ranking.components with their event names resolved against event_source.
 *
 * A PER_DAY component without its own problems list gets every FAULT event whose event_source.part equals its id,
 * so a new component can be added with an UPDATE of event_source.part and a block in the YAML.
 * Every event name must exist in event_source, so a misspelled name is reported instead of silently counting 0.
 *
 * Loaded on first use and kept until the application stops (changes of event_source.part need a restart for the
 * problems lists; part names shown elsewhere are read live). Not cached while it fails, so it is tried again.
 */
@Component
public class ComponentCatalog {

    /** One component as the REST API describes it (GET /api/components) and as the queries use it. */
    public record ComponentDefinition(String id, IssueMetric metric, List<String> work, List<String> problems,
                                      int minWork, int minDays, int minProblems, double ratio) {

        /** Work and problem names together, without duplicates (what the counting query reads). */
        public List<String> allSources() {
            Set<String> all = new LinkedHashSet<>(work);
            all.addAll(problems);
            return List.copyOf(all);
        }
    }

    /** One row of event_source. */
    public record EventSourceRow(String source, String category, String part) {
    }

    private final JdbcTemplate jdbc;
    private final RankingProperties ranking;
    private volatile List<ComponentDefinition> components;

    public ComponentCatalog(JdbcTemplate jdbc, RankingProperties ranking) {
        this.jdbc = jdbc;
        this.ranking = ranking;
    }

    /** Every component, in the order of ranking.components. */
    public List<ComponentDefinition> components() {
        List<ComponentDefinition> loaded = components;
        if (loaded == null) {
            synchronized (this) {
                loaded = components;
                if (loaded == null) {
                    loaded = resolve(ranking.components(), loadEventSources());
                    components = loaded;
                }
            }
        }
        return loaded;
    }

    /** The component with this id (any letter case); IllegalArgumentException (status 400) if there is none. */
    public ComponentDefinition require(String id) {
        String wanted = id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
        return components().stream()
                .filter(c -> c.id().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown component '" + id + "'. Use one of "
                        + components().stream().map(ComponentDefinition::id).toList()));
    }

    private List<EventSourceRow> loadEventSources() {
        Integer partColumns = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = 'event_source' and column_name = 'part'
                """, Integer.class);
        if (partColumns == null || partColumns == 0) {
            throw new IllegalStateException(
                    "Table event_source has no column part: create the schema again with db/schema.sql");
        }
        return jdbc.query("select source, category, part from event_source",
                (rs, rowNum) -> new EventSourceRow(rs.getString("source"), rs.getString("category"),
                        rs.getString("part")));
    }

    /** Pure Java: the rules with their event names resolved and checked. IllegalStateException on a bad name. */
    static List<ComponentDefinition> resolve(List<ComponentRule> rules, List<EventSourceRow> eventSources) {
        Map<String, EventSourceRow> bySource = eventSources.stream()
                .collect(Collectors.toMap(EventSourceRow::source, Function.identity(), (a, b) -> a));
        List<ComponentDefinition> result = new ArrayList<>();
        for (ComponentRule rule : rules) {
            List<String> problems = rule.problems();
            if (problems.isEmpty()) {
                problems = eventSources.stream()
                        .filter(e -> "FAULT".equals(e.category()) && rule.id().equalsIgnoreCase(e.part()))
                        .map(EventSourceRow::source)
                        .sorted()
                        .toList();
                if (problems.isEmpty()) {
                    throw new IllegalStateException("ranking.components " + rule.id() + ": no problem events. "
                            + "List them in 'problems', or set event_source.part = '" + rule.id()
                            + "' for its FAULT events");
                }
            }
            List<String> unknown = new ArrayList<>();
            rule.work().stream().filter(n -> !bySource.containsKey(n)).forEach(unknown::add);
            problems.stream().filter(n -> !bySource.containsKey(n)).forEach(unknown::add);
            if (!unknown.isEmpty()) {
                throw new IllegalStateException("ranking.components " + rule.id()
                        + ": these event names are not in event_source: " + unknown);
            }
            result.add(new ComponentDefinition(rule.id(), rule.metric(), rule.work(), problems, rule.minWork(),
                    rule.minDays(), rule.minProblems(), rule.ratio()));
        }
        return List.copyOf(result);
    }
}
