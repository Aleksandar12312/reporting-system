package com.example.monitor.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.monitor.stats.StatsRepository.Model;
import com.example.monitor.stats.StatsRepository.UnitRate;

/** Median, mean and counts per model. */
class ModelStatsCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);
    private static final List<Model> MODELS = List.of(new Model(1, "Duo"), new Model(2, "Pro"), new Model(3, "Recycler Max"));

    @Test
    void oddNumberOfUnitsTakesTheMiddleRate() {
        ModelStats duo = calculate(List.of(
                new UnitRate(1, 0.3, true, 27), new UnitRate(1, 0.1, true, 9), new UnitRate(1, 0.2, true, 18))).get(0);

        assertThat(duo.modelName()).isEqualTo("Duo");
        assertThat(duo.unitCount()).isEqualTo(3);
        assertThat(duo.baselineUnitCount()).isEqualTo(3);
        assertThat(duo.faultsInWindow()).isEqualTo(54);
        assertThat(duo.medianRatePerDay()).isCloseTo(0.2, within(1e-12));
        assertThat(duo.meanRatePerDay()).isCloseTo(0.2, within(1e-12));
        assertThat(duo.windowDays()).isEqualTo(90);
        assertThat(duo.computedAt()).isEqualTo(NOW);
    }

    @Test
    void evenNumberOfUnitsAveragesTheTwoMiddleRates() {
        ModelStats duo = calculate(List.of(
                new UnitRate(1, 0.1, true, 1), new UnitRate(1, 0.2, true, 1),
                new UnitRate(1, 0.4, true, 1), new UnitRate(1, 2.0, true, 1))).get(0);

        assertThat(duo.medianRatePerDay()).isCloseTo(0.3, within(1e-12));
        // The mean is pulled up by the one bad unit, the median is not
        assertThat(duo.meanRatePerDay()).isCloseTo(0.675, within(1e-12));
    }

    @Test
    void unitsWithTooLittleHistoryAreCountedButNotInTheMedian() {
        ModelStats pro = calculate(List.of(
                new UnitRate(2, 0.1, true, 9), new UnitRate(2, 5.0, false, 15))).get(1);

        assertThat(pro.unitCount()).isEqualTo(2);
        assertThat(pro.baselineUnitCount()).isEqualTo(1);
        assertThat(pro.faultsInWindow()).isEqualTo(24);
        assertThat(pro.medianRatePerDay()).isCloseTo(0.1, within(1e-12));
        assertThat(pro.meanRatePerDay()).isCloseTo(0.1, within(1e-12));
    }

    @Test
    void aModelWithoutBaselineUnitsHasNoMedianAndNoMean() {
        ModelStats pro = calculate(List.of(new UnitRate(2, 1.0, false, 3))).get(1);

        assertThat(pro.unitCount()).isEqualTo(1);
        assertThat(pro.medianRatePerDay()).isNull();
        assertThat(pro.meanRatePerDay()).isNull();
    }

    @Test
    void everyModelGetsARowEvenWithoutUnits() {
        List<ModelStats> rows = calculate(List.of());

        assertThat(rows).hasSize(3);
        ModelStats max = rows.get(2);
        assertThat(max.modelName()).isEqualTo("Recycler Max");
        assertThat(max.unitCount()).isZero();
        assertThat(max.faultsInWindow()).isZero();
        assertThat(max.medianRatePerDay()).isNull();
    }

    @Test
    void aZeroMedianStaysZero() {
        ModelStats duo = calculate(List.of(new UnitRate(1, 0, true, 0), new UnitRate(1, 0, true, 0))).get(0);

        assertThat(duo.medianRatePerDay()).isZero();
        assertThat(duo.meanRatePerDay()).isZero();
    }

    private static List<ModelStats> calculate(List<UnitRate> units) {
        return ModelStatsCalculator.calculate(MODELS, units, 90, NOW);
    }
}
