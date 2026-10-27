package com.example.monitor.stats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.monitor.stats.StatsRepository.Model;
import com.example.monitor.stats.StatsRepository.UnitRate;

/**
 * Pure Java, no database: one model_stats row per model from the unit_stats rows.
 * The median and the mean use only the baseline units (enough history),;
 * the unit count and the faults use every unit of the model.
 */
public final class ModelStatsCalculator {

    private ModelStatsCalculator() {
    }

    /** One row per model, also for a model without units (counts 0, median and mean null). */
    public static List<ModelStats> calculate(List<Model> models, List<UnitRate> units, int windowDays,
                                             LocalDateTime computedAt) {
        Map<Long, List<UnitRate>> byModel = units.stream().collect(Collectors.groupingBy(UnitRate::unitTypeId));
        List<ModelStats> result = new ArrayList<>();
        for (Model model : models) {
            List<UnitRate> modelUnits = byModel.getOrDefault(model.id(), List.of());
            double[] baselineRates = modelUnits.stream()
                    .filter(UnitRate::inBaseline)
                    .mapToDouble(UnitRate::ratePerDay)
                    .sorted()
                    .toArray();
            long faults = modelUnits.stream().mapToLong(UnitRate::faultsInWindow).sum();
            result.add(new ModelStats(model.id(), model.name(), windowDays, modelUnits.size(), baselineRates.length,
                    faults, median(baselineRates), mean(baselineRates), computedAt));
        }
        return result;
    }

    /** Median of sorted values: the middle one, or the average of the two middle ones; null when empty. */
    static Double median(double[] sorted) {
        int n = sorted.length;
        if (n == 0) {
            return null;
        }
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    static Double mean(double[] values) {
        if (values.length == 0) {
            return null;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
