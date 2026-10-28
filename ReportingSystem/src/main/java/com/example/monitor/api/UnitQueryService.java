package com.example.monitor.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.monitor.api.ComponentCatalog.ComponentDefinition;
import com.example.monitor.api.IssueEngine.Counts;
import com.example.monitor.stats.StatsProperties;

/**
 * What the REST controller calls. Read-only: it loads the numbers with {@link UnitReadRepository}
 * and lets {@link RankingEngine} decide the tiers and {@link IssueEngine} the component messages.
 * Bad parameters throw IllegalArgumentException (status 400).
 */
@Service
public class UnitQueryService {

    /** Largest page of events or ranking rows one call may ask for. */
    public static final int MAX_LIMIT = 1000;

    private final UnitReadRepository repository;
    private final StatsProperties stats;
    private final RankingEngine engine;
    private final IssueRepository issueRepository;
    private final IssueEngine issueEngine;
    private final ComponentCatalog catalog;

    public UnitQueryService(UnitReadRepository repository, StatsProperties stats, RankingProperties ranking,
                            IssueRepository issueRepository, ComponentCatalog catalog) {
        this.repository = repository;
        this.stats = stats;
        this.engine = new RankingEngine(ranking);
        this.issueRepository = issueRepository;
        this.issueEngine = new IssueEngine(ranking, stats.baselineMinDays());
        this.catalog = catalog;
    }

    /** Every unit, highest ratio first, with its component messages. */
    public List<UnitScore> scoreAllUnits() {
        Map<Long, List<ComponentIssue>> issues = evaluateIssues();
        return engine.score(repository.loadScoreRows(), repository.loadPartCounts()).stream()
                .map(s -> s.withIssues(issues.getOrDefault(s.unitId(), List.of())))
                .toList();
    }

    /** Component messages of every unit (the model medians need all units, also for one unit). */
    private Map<Long, List<ComponentIssue>> evaluateIssues() {
        Map<String, List<Counts>> counts = new LinkedHashMap<>();
        for (ComponentDefinition component : catalog.components()) {
            counts.put(component.id(), issueRepository.loadCounts(component));
        }
        return issueEngine.evaluate(issueRepository.loadUnitWindows(), counts, issueRepository.loadLastOks());
    }

    /**
     * Marks one component of a unit as OK; returns the unit as it is now.
     * 404 if the unit is unknown, 400 if the component is not in ranking.components.
     */
    public UnitScore markOk(long unitId, String component, AckRequest request) {
        getUnitScore(unitId);
        String id = catalog.require(component).id();
        String[] byAndNote = checkAck(request);
        issueRepository.insertAck(unitId, id, nowUtc(), byAndNote[0], byAndNote[1]);
        return getUnitScore(unitId);
    }

    /**
     * Marks every component of a unit that is ACTIVE or RECURRING as OK (the others stay as they are, so their
     * counting is not restarted); returns the unit as it is now. 404 if the unit is unknown.
     */
    @Transactional
    public UnitScore markAllOk(long unitId, AckRequest request) {
        UnitScore current = getUnitScore(unitId);
        String[] byAndNote = checkAck(request);
        LocalDateTime now = nowUtc();
        for (ComponentIssue issue : current.issues()) {
            if (issue.status() != IssueStatus.OK) {
                issueRepository.insertAck(unitId, issue.component(), now, byAndNote[0], byAndNote[1]);
            }
        }
        return getUnitScore(unitId);
    }

    /** Every "marked OK" of one unit, newest first. */
    public List<IssueAck> getAckHistory(long unitId, int limit) {
        checkLimit(limit);
        return issueRepository.loadHistory(unitId, limit);
    }

    /** Checks the body of a "mark as OK" call; returns {by, note} trimmed (note may be null). */
    private static String[] checkAck(AckRequest request) {
        String by = request == null || request.by() == null ? "" : request.by().trim();
        if (by.isEmpty()) {
            throw new IllegalArgumentException("Field 'by' is required (who marks it as OK)");
        }
        if (by.length() > 100) {
            throw new IllegalArgumentException("Field 'by' must be at most 100 characters");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("Field 'note' must be at most 500 characters");
        }
        return new String[] {by, note};
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** One unit; throws {@link UnitNotFoundException} (status 404) if it has no row in unit_stats. */
    public UnitScore getUnitScore(long unitId) {
        List<UnitScore> scores = engine.score(repository.loadScoreRows(unitId), repository.loadPartCounts(unitId));
        if (scores.isEmpty()) {
            throw new UnitNotFoundException(unitId);
        }
        return scores.get(0).withIssues(evaluateIssues().getOrDefault(unitId, List.of()));
    }

    /** Tier counts, window days and the time of the last statistics run. */
    public Summary getSummary() {
        List<UnitScore> scores = scoreAllUnits();
        Map<Tier, Integer> tiers = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            tiers.put(tier, 0);
        }
        Map<String, Integer> issues = new LinkedHashMap<>();
        for (ComponentDefinition component : catalog.components()) {
            issues.put(component.id(), 0);
        }
        for (UnitScore score : scores) {
            tiers.merge(score.tier(), 1, Integer::sum);
            for (ComponentIssue issue : score.issues()) {
                if (issue.status() != IssueStatus.OK && issues.containsKey(issue.component())) {
                    issues.merge(issue.component(), 1, Integer::sum);
                }
            }
        }
        return new Summary(repository.loadLastComputedAt(), stats.windowDays(), scores.size(), tiers, issues);
    }

    /** One row per model. */
    public List<ModelStatsRow> getModels() {
        return repository.loadModelStats();
    }

    /** The components that get their own message (ranking.components), with their resolved event names. */
    public List<ComponentDefinition> getComponents() {
        return catalog.components();
    }

    /** The fault kinds the system counts, in the configured order. */
    public List<String> getFaultKinds() {
        return stats.faultSources();
    }

    /** Only WATCH and CRITICAL units. */
    public List<UnitScore> findProblematicUnits() {
        return scoreAllUnits().stream().filter(UnitScore::isFlagged).toList();
    }

    /**
     * The N worst units: CRITICAL first, then WATCH, OK and NO_BASELINE; inside a tier the highest ratio first.
     * The tier comes first so that a new unit with one fault (a huge ratio but still OK) does not outrank a CRITICAL one.
     */
    public List<UnitScore> getWorstUnits(int limit) {
        checkLimit(limit);
        return scoreAllUnits().stream()
                .sorted(Comparator.comparing(UnitScore::tier))
                .limit(limit)
                .toList();
    }

    public List<FaultBreakdown> getFaultBreakdown(long unitId) {
        return repository.loadBreakdown(unitId);
    }

    /** Newest events of one unit, all kinds, over its whole history. */
    public List<UnitEvent> getUnitEvents(long unitId, int limit) {
        checkLimit(limit);
        return repository.loadEvents(unitId, false, limit);
    }

    /** Newest fault events of one unit (only the configured fault sources), over its whole history. */
    public List<UnitEvent> getUnitFaultEvents(long unitId, int limit) {
        checkLimit(limit);
        return repository.loadEvents(unitId, true, limit);
    }

    /**
     * Units with the most faults of one kind (e.g. JAMMED) in their window.
     *
     * @param fault one of the configured fault sources, in any letter case
     */
    public List<FaultRanking> getTopUnitsByFault(String fault, int limit) {
        checkLimit(limit);
        if (fault == null || fault.isBlank()) {
            throw new IllegalArgumentException("Parameter 'fault' is required. Use one of " + stats.faultSources());
        }
        String normalized = fault.trim().toUpperCase(Locale.ROOT);
        if (!stats.faultSources().contains(normalized)) {
            throw new IllegalArgumentException("Unknown fault '" + fault + "'. Use one of " + stats.faultSources());
        }
        return repository.loadFaultRanking(normalized, limit);
    }

    private static void checkLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }
}
