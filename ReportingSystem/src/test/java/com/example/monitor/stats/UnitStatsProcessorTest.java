package com.example.monitor.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** Window, exposure, rate and baseline of one unit. */
class UnitStatsProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);

    private final UnitStatsProcessor processor = new UnitStatsProcessor(NOW, 90, 14);

    @Test
    void anOldUnitIsObservedForTheWholeWindow() {
        UnitStats s = processor.process(new UnitFaultTotals(7, 2, NOW.minusDays(400), 18, 40, LocalDate.of(2026, 10, 26)));

        assertThat(s.unitId()).isEqualTo(7L);
        assertThat(s.unitTypeId()).isEqualTo(2L);
        assertThat(s.windowStart()).isEqualTo(NOW.minusDays(90));
        assertThat(s.exposureDays()).isCloseTo(90.0, within(1e-9));
        assertThat(s.faultsInWindow()).isEqualTo(18);
        assertThat(s.ratePerDay()).isCloseTo(0.2, within(1e-9));
        assertThat(s.inBaseline()).isTrue();
        assertThat(s.totalFaults()).isEqualTo(40);
        assertThat(s.lastFaultDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(s.computedAt()).isEqualTo(NOW);
    }

    @Test
    void aYoungUnitIsObservedSinceItsCreationAndIsNotInTheBaseline() {
        UnitStats s = processor.process(new UnitFaultTotals(8, 1, NOW.minusDays(3).minusHours(12), 7, 7, NOW.toLocalDate()));

        assertThat(s.windowStart()).isEqualTo(NOW.minusDays(3).minusHours(12));
        assertThat(s.exposureDays()).isCloseTo(3.5, within(1e-9));
        assertThat(s.ratePerDay()).isCloseTo(2.0, within(1e-9));
        assertThat(s.inBaseline()).isFalse();
    }

    @Test
    void aNewUnitWithoutFaultsStillGetsARow() {
        UnitStats s = processor.process(new UnitFaultTotals(9, 1, NOW.minusHours(1), 0, 0, null));

        assertThat(s.faultsInWindow()).isZero();
        assertThat(s.ratePerDay()).isZero();
        assertThat(s.lastFaultDate()).isNull();
        assertThat(s.inBaseline()).isFalse();
    }

    @Test
    void exactlyTheBaselineDaysIsEnough() {
        assertThat(processor.process(new UnitFaultTotals(1, 1, NOW.minusDays(14), 1, 1, null)).inBaseline()).isTrue();
    }

    @Test
    void aCreationDateInTheFutureGivesAnEmptyWindow() {
        UnitStats s = processor.process(new UnitFaultTotals(1, 1, NOW.plusDays(1), 0, 0, null));

        assertThat(s.exposureDays()).isZero();
        assertThat(s.ratePerDay()).isZero();
    }

    @Test
    void aMissingCreationDateUsesTheWholeWindow() {
        assertThat(processor.process(new UnitFaultTotals(1, 1, null, 9, 9, null)).exposureDays())
                .isCloseTo(90.0, within(1e-9));
    }
}
