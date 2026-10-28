package com.example.monitor.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.example.monitor.mock.EventSimulator.MockEvent;
import com.example.monitor.mock.EventSimulator.Profile;
import com.example.monitor.mock.EventSimulator.Rates;
import com.example.monitor.mock.EventSimulator.SimUnit;
import com.example.monitor.recycler.EventCategory;
import com.example.monitor.recycler.RecyclerEvent;

class EventSimulatorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final Rates RATES = new Rates(10, 2);
    private static final Set<String> FAULT_SOURCES = Set.copyOf(RecyclerEvent.faultSources());

    @Test
    void currentTickRoundsDownToFiveMinutes() {
        assertThat(EventSimulator.currentTick(LocalDateTime.of(2026, 1, 1, 12, 7, 33)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 5));
        assertThat(EventSimulator.currentTick(LocalDateTime.of(2026, 1, 1, 12, 5, 0)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 5));
    }

    @Test
    void tickOfEventIsTheTickWhoseWindowContainsTheEvent() {
        assertThat(EventSimulator.tickOfEvent(LocalDateTime.of(2026, 1, 1, 12, 3, 20)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 5));
        // window of tick 12:05 is [12:00:00, 12:04:59]
        assertThat(EventSimulator.tickOfEvent(LocalDateTime.of(2026, 1, 1, 12, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 5));
        assertThat(EventSimulator.tickOfEvent(LocalDateTime.of(2026, 1, 1, 12, 4, 59)))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 5));
    }

    @Test
    void silentUnitNeverReportsAnything() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 0.3, Profile.SILENT);
        assertThat(simulate(unit, 20, 1)).isEmpty();
    }

    @Test
    void unitReportsNothingBeforeItWasCreated() {
        SimUnit unit = new SimUnit(1, START.plusDays(100), 0.3, Profile.BAD);
        assertThat(simulate(unit, 20, 1)).isEmpty();
    }

    @Test
    void eventsFallInsideTheTickWindow() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 50, Profile.BAD);  // many faults, so jams are tested too
        LocalDateTime tick = START.plusHours(1);
        Rates busy = new Rates(2000, 300);                                   // several transactions per tick
        for (int i = 0; i < 5_000; i++) {
            for (MockEvent e : EventSimulator.simulateTick(unit, tick, new Random(i), busy)) {
                assertThat(e.eventTime()).isAfterOrEqualTo(tick.minusMinutes(5)).isBefore(tick);
            }
        }
    }

    @Test
    void sameRandomSeedGivesSameEvents() {
        SimUnit unit = new SimUnit(7, START.minusDays(500), 0.3, Profile.BAD);
        assertThat(simulate(unit, 10, 42)).isEqualTo(simulate(unit, 10, 42));
    }

    @Test
    void everyEventIsARecyclerEventOfComponentRc() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 0.3, Profile.BAD);
        List<MockEvent> events = simulate(unit, 30, 1);
        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> RecyclerEvent.fromSource(e.source()).isPresent());
        assertThat(events).allMatch(e -> e.placeholder().equals("RC") && e.type().equals("PLACEHOLDER"));
    }

    @Test
    void commandRepliesAndCoinOrTicketEventsAreNeverSent() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 0.3, Profile.BAD);
        Set<String> sent = simulate(unit, 90, 1).stream().map(MockEvent::source).collect(Collectors.toSet());
        assertThat(sent).noneMatch(s -> RecyclerEvent.fromSource(s).orElseThrow().category() == EventCategory.COMMAND);
        assertThat(sent).noneMatch(s -> s.startsWith("COIN_") || s.startsWith("BAR_CODE_"));
    }

    @Test
    void badUnitHasFarMoreFaultsThanNormalOne() {
        SimUnit normal = new SimUnit(1, START.minusDays(500), 0.15, Profile.NORMAL);
        SimUnit bad = new SimUnit(2, START.minusDays(500), 0.15, Profile.BAD);
        long normalFaults = faults(simulate(normal, 90, 1));
        long badFaults = faults(simulate(bad, 90, 1));
        // expected: 0.15 * 90 = ~13 vs 0.15 * 8 * 90 = ~108
        assertThat(normalFaults).isBetween(3L, 35L);
        assertThat(badFaults).isBetween(70L, 150L);
    }

    @Test
    void badOneComponentConcentratesOnOneFaultKind() {
        SimUnit unit = new SimUnit(3, START.minusDays(500), 0.3, Profile.BAD_ONE_COMPONENT);
        List<MockEvent> faults = simulate(unit, 90, 1).stream().filter(EventSimulatorTest::isFault).toList();
        long top = faults.stream()
                .collect(Collectors.groupingBy(e -> e.placeholder() + "/" + e.source(), Collectors.counting()))
                .values().stream().max(Long::compare).orElse(0L);
        assertThat((double) top / faults.size()).isGreaterThan(0.7);
    }

    @Test
    void withoutFaultRateThereAreNoFaultsButStillOperationsAndWarnings() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 0.0, Profile.NORMAL);
        List<MockEvent> events = simulate(unit, 30, 1);
        assertThat(events).isNotEmpty();
        assertThat(events).noneMatch(EventSimulatorTest::isFault);
        Map<EventCategory, Long> byCategory = events.stream().collect(Collectors.groupingBy(
                e -> RecyclerEvent.fromSource(e.source()).orElseThrow().category(), Collectors.counting()));
        assertThat(byCategory).containsKeys(EventCategory.OPERATION, EventCategory.WARNING, EventCategory.STATUS);
    }

    @Test
    void transactionsGiveAboutTheConfiguredNumberOfDeposits() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 0.0, Profile.NORMAL);
        long reads = simulate(unit, 30, 1).stream().filter(e -> e.source().equals("READ_NOTE")).count();
        // 10 transactions a day, 68% of them deposits: ~204 in 30 days
        assertThat(reads).isBetween(150L, 260L);
    }

    @Test
    void everyJamIsFollowedByAJamRecovery() {
        SimUnit unit = new SimUnit(1, START.minusDays(500), 2.0, Profile.BAD);
        List<MockEvent> events = simulate(unit, 10, 1);
        long jams = events.stream().filter(e -> e.source().endsWith("JAM") || e.source().equals("JAMMED")).count();
        long recoveries = events.stream().filter(e -> e.source().equals("JAM_RECOVERY")).count();
        assertThat(jams).isGreaterThan(0L);
        assertThat(recoveries).isEqualTo(jams);
    }

    private static boolean isFault(MockEvent e) {
        return FAULT_SOURCES.contains(e.source());
    }

    private static long faults(List<MockEvent> events) {
        return events.stream().filter(EventSimulatorTest::isFault).count();
    }

    private static List<MockEvent> simulate(SimUnit unit, int days, long seed) {
        Random rng = new Random(seed);
        List<MockEvent> out = new ArrayList<>();
        int ticks = days * 24 * 60 / EventSimulator.TICK_MINUTES;
        for (int i = 1; i <= ticks; i++) {
            out.addAll(EventSimulator.simulateTick(unit, START.plusMinutes((long) i * EventSimulator.TICK_MINUTES), rng, RATES));
        }
        return out;
    }
}
