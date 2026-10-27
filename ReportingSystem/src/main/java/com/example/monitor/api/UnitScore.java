package com.example.monitor.api;

import java.util.List;

/**
 * One unit as the REST API returns it (GET /api/units/scores and similar).
 *
 * @param faults          faults in the unit's window (unit_stats.faults_in_window)
 * @param exposureDays    length of the window in days
 * @param ratePerDay      faults divided by exposure days
 * @param modelMedianRate median rate of the units of the same model; null if the model has no baseline unit
 * @param ratioToModel    ratePerDay divided by modelMedianRate; null if there is no usable median
 * @param topPart         component with the most faults; null if the unit has no faults
 * @param topPartShare    topPartFaults divided by faults (0 to 1); null if the unit has no faults
 * @param scope           PART or GENERAL for flagged units with enough faults, otherwise null
 * @param issues          one entry per component (SCANNER, RECYCLER): is something wrong with it
 */
public record UnitScore(
        long unitId,
        String serialNumber,
        String modelName,
        int faults,
        double exposureDays,
        double ratePerDay,
        Double modelMedianRate,
        Double ratioToModel,
        Tier tier,
        String topPart,
        int topPartFaults,
        Double topPartShare,
        ProblemScope scope,
        List<ComponentIssue> issues) {

    public UnitScore {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** The same score with the given component messages. */
    public UnitScore withIssues(List<ComponentIssue> newIssues) {
        return new UnitScore(unitId, serialNumber, modelName, faults, exposureDays, ratePerDay, modelMedianRate,
                ratioToModel, tier, topPart, topPartFaults, topPartShare, scope, newIssues);
    }

    /** True for WATCH and CRITICAL. */
    public boolean isFlagged() {
        return tier == Tier.WATCH || tier == Tier.CRITICAL;
    }
}
