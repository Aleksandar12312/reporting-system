package com.example.monitor.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;

/** The new events are read in slices, and the state moves on with every slice. */
class UnitEventDailyTaskletTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0, 30);

    private final FakeStatsRepository stats = new FakeStatsRepository();
    private final UnitEventDailyTasklet tasklet = new UnitEventDailyTasklet(stats, 500);

    @Test
    void aFullLoadIsReadInSlicesUntilTheLastEvent() {
        stats.state = Optional.of(new EtlState(0, null, null, null));

        assertThat(tasklet.processNextSlice(1_200, NOW)).isEqualTo(RepeatStatus.CONTINUABLE);
        assertThat(tasklet.processNextSlice(1_200, NOW)).isEqualTo(RepeatStatus.CONTINUABLE);
        assertThat(tasklet.processNextSlice(1_200, NOW)).isEqualTo(RepeatStatus.FINISHED);

        assertThat(stats.addedSlices).hasSize(3);
        assertThat(stats.addedSlices.get(0)).containsExactly(0, 500);
        assertThat(stats.addedSlices.get(1)).containsExactly(500, 1_000);
        assertThat(stats.addedSlices.get(2)).containsExactly(1_000, 1_200);
        assertThat(stats.savedStates).containsExactly(500L, 1_000L, 1_200L);
    }

    @Test
    void anUpdateReadsOnlyTheNewEvents() {
        stats.state = Optional.of(new EtlState(1_200, 1_200L, 3L, NOW.minusMinutes(5)));

        assertThat(tasklet.processNextSlice(1_250, NOW)).isEqualTo(RepeatStatus.FINISHED);

        assertThat(stats.addedSlices).hasSize(1);
        assertThat(stats.addedSlices.get(0)).containsExactly(1_200, 1_250);
        assertThat(stats.savedStates).containsExactly(1_250L);
    }

    @Test
    void nothingNewMeansNothingIsWritten() {
        stats.state = Optional.of(new EtlState(1_200, 1_200L, 3L, NOW.minusMinutes(5)));

        assertThat(tasklet.processNextSlice(1_200, NOW)).isEqualTo(RepeatStatus.FINISHED);

        assertThat(stats.addedSlices).isEmpty();
        assertThat(stats.savedStates).isEmpty();
    }
}
