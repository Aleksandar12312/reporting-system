package com.example.monitor.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** When the statistics are updated and when they are built again from the start. */
class PrepareStatsTaskletTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0, 30);

    private final FakeStatsRepository stats = new FakeStatsRepository();
    private final PrepareStatsTasklet tasklet = new PrepareStatsTasklet(stats);

    @Test
    void theFirstRunBuildsEverythingFromTheStart() {
        stats.maxEventId = 1_000;

        long toEventId = tasklet.prepare(NOW);

        assertThat(toEventId).isEqualTo(1_000);
        assertThat(stats.deleteAllCalls).isEqualTo(1);
        assertThat(stats.savedStates).containsExactly(0L);
    }

    @Test
    void aNormalRunOnlyUpdates() {
        stats.state = Optional.of(new EtlState(900, 900L, 3L, NOW.minusMinutes(5)));
        stats.maxEventId = 1_000;

        long toEventId = tasklet.prepare(NOW);

        assertThat(toEventId).isEqualTo(1_000);
        assertThat(stats.deleteAllCalls).isZero();
        assertThat(stats.savedStates).isEmpty();
    }

    @Test
    void fewerEventsThanAlreadyCountedMeansTheEventTableWasRecreated() {
        stats.state = Optional.of(new EtlState(900, 900L, 3L, NOW));
        stats.maxEventId = 500;

        assertThat(tasklet.rebuildReason(stats.state, stats.maxEventId)).isPresent();
        tasklet.prepare(NOW);
        assertThat(stats.deleteAllCalls).isEqualTo(1);
    }

    @Test
    void aChangedCheckEventMeansTheEventTableWasRecreated() {
        // After a re-seed there can be MORE events than before, so the id alone does not show it
        stats.state = Optional.of(new EtlState(900, 900L, 3L, NOW));
        stats.maxEventId = 5_000;
        stats.checkEventUnchanged = false;

        assertThat(tasklet.rebuildReason(stats.state, stats.maxEventId)).isPresent();
    }

    @Test
    void anEmptiedDailyTableIsBuiltAgain() {
        stats.state = Optional.of(new EtlState(900, 900L, 3L, NOW));
        stats.maxEventId = 1_000;
        stats.dailyEmpty = true;

        assertThat(tasklet.rebuildReason(stats.state, stats.maxEventId)).isPresent();
    }

    @Test
    void anEmptyDailyTableIsFineWhenThereAreNoFaultsAtAll() {
        stats.state = Optional.of(new EtlState(900, 900L, 3L, NOW));
        stats.maxEventId = 1_000;
        stats.dailyEmpty = true;
        stats.faultEventsExist = false;

        assertThat(tasklet.rebuildReason(stats.state, stats.maxEventId)).isEmpty();
    }

    @Test
    void anEmptyEventTableGivesNothingToRead() {
        stats.state = Optional.of(new EtlState(0, null, null, null));
        stats.maxEventId = 0;

        assertThat(tasklet.prepare(NOW)).isZero();
        assertThat(stats.deleteAllCalls).isZero();
    }
}
