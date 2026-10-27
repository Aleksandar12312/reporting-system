package com.example.monitor.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the numbers of the statistics tables into tiers and scopes. Pure Java: no Spring, no database.
 * The rate, the exposure days and the model median are already calculated by the statistics job.
 *
 * <ol>
 *   <li>ratio = rate / median of the unit's model (null when there is no usable median)</li>
 *   <li>tier: fewer than min-faults faults is OK; no ratio is NO_BASELINE;
 *       ratio >= critical-ratio is CRITICAL; ratio >= watch-ratio is WATCH; otherwise OK</li>
 *   <li>for WATCH and CRITICAL units with enough faults: PART if one component causes at least
 *       part-share of the faults, otherwise GENERAL</li>
 * </ol>
 */
public final class RankingEngine {

    /** One row of unit_stats with the unit's serial number, model name and the model's median. */
    public record ScoreRow(long unitId, String serialNumber, String modelName, int faults, double exposureDays,
                           double ratePerDay, Double modelMedianRate) {
    }

    /** Faults of one unit that come from one component in the unit's window. */
    public record PartCount(long unitId, String part, int faults) {
    }

    /** Highest ratio first; units without a ratio last; equal ratios by unit id. */
    private static final Comparator<UnitScore> WORST_FIRST = Comparator
            .comparing(UnitScore::ratioToModel, Comparator.nullsLast(Comparator.<Double>reverseOrder()))
            .thenComparingLong(UnitScore::unitId);

    private final RankingProperties props;

    public RankingEngine(RankingProperties props) {
        this.props = props;
    }

    /** One score per row, worst first. */
    public List<UnitScore> score(List<ScoreRow> rows, List<PartCount> partCounts) {
        Map<Long, PartCount> topParts = topPartPerUnit(partCounts);
        List<UnitScore> scores = new ArrayList<>(rows.size());
        for (ScoreRow r : rows) {
            Double median = r.modelMedianRate();
            Double ratio = (median == null || median <= 0) ? null : r.ratePerDay() / median;
            Tier tier = tierOf(r.faults(), ratio);

            PartCount top = topParts.get(r.unitId());
            Double share = (top == null || r.faults() <= 0)
                    ? null
                    : Math.min(1.0, (double) top.faults() / r.faults());
            ProblemScope scope = scopeOf(tier, r.faults(), share);

            scores.add(new UnitScore(
                    r.unitId(), r.serialNumber(), r.modelName(), r.faults(), r.exposureDays(), r.ratePerDay(),
                    median, ratio, tier,
                    top == null ? null : top.part(),
                    top == null ? 0 : top.faults(),
                    share, scope));
        }
        scores.sort(WORST_FIRST);
        return List.copyOf(scores);
    }

    private Tier tierOf(int faults, Double ratio) {
        if (faults < props.minFaults()) {
            return Tier.OK;
        }
        if (ratio == null) {
            return Tier.NO_BASELINE;
        }
        if (ratio >= props.criticalRatio()) {
            return Tier.CRITICAL;
        }
        if (ratio >= props.watchRatio()) {
            return Tier.WATCH;
        }
        return Tier.OK;
    }

    private ProblemScope scopeOf(Tier tier, int faults, Double share) {
        boolean flagged = tier == Tier.WATCH || tier == Tier.CRITICAL;
        if (!flagged || share == null || faults < props.partMinFaults()) {
            return null;
        }
        return share >= props.partShare() ? ProblemScope.PART : ProblemScope.GENERAL;
    }

    /** The component with the most faults per unit; on a tie the alphabetically first name. */
    private static Map<Long, PartCount> topPartPerUnit(List<PartCount> partCounts) {
        Map<Long, PartCount> best = new HashMap<>();
        for (PartCount c : partCounts) {
            best.merge(c.unitId(), c, (a, b) -> isBetter(b, a) ? b : a);
        }
        return best;
    }

    private static boolean isBetter(PartCount candidate, PartCount current) {
        if (candidate.faults() != current.faults()) {
            return candidate.faults() > current.faults();
        }
        return candidate.part().compareTo(current.part()) < 0;
    }
}
