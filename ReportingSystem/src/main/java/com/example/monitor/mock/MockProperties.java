package com.example.monitor.mock;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All generator rules live here and are filled from application-dev.yml (prefix "mock").
 */
@ConfigurationProperties(prefix = "mock")
public record MockProperties(
        boolean enabled,
        long seed,
        int unitCount,
        int backfillDays,
        double transactionsPerDay,
        double statusEventsPerDay,
        List<ModelRule> models,
        Planted planted) {

    /** Missing sections in the YAML become empty instead of causing a NullPointerException later. */
    public MockProperties {
        models = models == null ? List.of() : List.copyOf(models);
        planted = planted == null ? new Planted(0, 0, 0, 0, 0) : planted;
    }

    /** Customer transactions and warnings/state changes per unit per day, as the simulator needs them. */
    public EventSimulator.Rates rates() {
        return new EventSimulator.Rates(transactionsPerDay, statusEventsPerDay);
    }

    /** A machine model and how many faults per day a NORMAL unit of this model has. */
    public record ModelRule(String name, double faultsPerDay) {
    }

    /** How many units get a special behaviour. The rest of the units are NORMAL. */
    public record Planted(int bad, int badOneComponent, int borderline, int newFewFaults, int silent) {
        public int total() {
            return bad + badOneComponent + borderline + newFewFaults + silent;
        }
    }
}
