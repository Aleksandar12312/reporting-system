package com.example.monitor.api;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.monitor.stats.StatsProperties;

/**
 * What the REST controller calls. Read-only: it loads the numbers with {@link UnitReadRepository}
 * and lets {@link RankingEngine} decide the tiers. Bad parameters throw IllegalArgumentException (status 400).
 */
@Service
public class UnitQueryService {

    /** Largest page of events or ranking rows one call may ask for. */
    public static final int MAX_LIMIT = 1000;

    private final UnitReadRepository repository;
    private final StatsProperties stats;
    private final RankingEngine engine;

    public UnitQueryService(UnitReadRepository repository, StatsProperties stats, RankingProperties ranking) {
        this.repository = repository;
        this.stats = stats;
        this.engine = new RankingEngine(ranking);
    }

    /** Every unit, highest ratio first. */
    public List<UnitScore> scoreAllUnits() {
        return engine.score(repository.loadScoreRows(), repository.loadPartCounts());
    }

    /** One unit; throws {@link UnitNotFoundException} (status 404) if it has no row in unit_stats. */
    public UnitScore getUnitScore(long unitId) {
        List<UnitScore> scores = engine.score(repository.loadScoreRows(unitId), repository.loadPartCounts(unitId));
        if (scores.isEmpty()) {
            throw new UnitNotFoundException(unitId);
        }
        return scores.get(0);
    }

    /** Tier counts, window days and the time of the last statistics run. */
    public Summary getSummary() {
        List<UnitScore> scores = scoreAllUnits();
        Map<Tier, Integer> tiers = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            tiers.put(tier, 0);
        }
        for (UnitScore score : scores) {
            tiers.merge(score.tier(), 1, Integer::sum);
        }
        return new Summary(repository.loadLastComputedAt(), stats.windowDays(), scores.size(), tiers);
    }

    /** One row per model. */
    public List<ModelStatsRow> getModels() {
        return repository.loadModelStats();
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
