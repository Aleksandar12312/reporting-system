package com.example.monitor.mock;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.monitor.mock.EventSimulator.MockEvent;
import com.example.monitor.mock.EventSimulator.Profile;
import com.example.monitor.mock.EventSimulator.SimUnit;
import com.example.monitor.mock.MockProperties.ModelRule;

/**
 * Plays the role of the machines: every 5 minutes (at :00, :05, :10 ... UTC)
 * each unit "sends" whatever it reports in that interval, and it lands in the event table.
 *
 * If the application was stopped for a while, the next run also plays the ticks it missed
 * (at most {@code backfillDays} back), so the event history has no hole.
 * Each tick is random-seeded by (seed, tick, unit), so replaying a tick that was already
 * played gives the same result and never produces duplicates for a tick that had events.
 */
@Component
@ConditionalOnProperty(prefix = "mock", name = "enabled", havingValue = "true")
public class MockEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(MockEventScheduler.class);

    private final JdbcTemplate jdbc;
    private final MockProperties props;
    private final EventWriter writer;
    private final Map<String, Double> faultsPerDayByModel;

    public MockEventScheduler(JdbcTemplate jdbc, MockProperties props, EventWriter writer) {
        this.jdbc = jdbc;
        this.props = props;
        this.writer = writer;
        this.faultsPerDayByModel = props.models().stream()
                .collect(Collectors.toMap(ModelRule::name, ModelRule::faultsPerDay));
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void sendEvents() {
        LocalDateTime currentTick = EventSimulator.currentTick(LocalDateTime.now(ZoneOffset.UTC));
        LocalDateTime firstTick = firstMissingTick(currentTick);
        List<SimUnit> units = loadUnits();

        List<MockEvent> events = new ArrayList<>();
        int ticks = 0;
        for (LocalDateTime tick = firstTick; !tick.isAfter(currentTick);
             tick = tick.plusMinutes(EventSimulator.TICK_MINUTES)) {
            ticks++;
            for (SimUnit unit : units) {
                Random rng = new Random(tickSeed(tick, unit.id()));
                events.addAll(EventSimulator.simulateTick(unit, tick, rng, props.rates()));
            }
        }
        // Same as the seeder: event ids grow with event time
        events.sort(Comparator.comparing(MockEvent::eventTime));
        writer.saveAll(events);
        log.info("Ticks {}..{} ({}): {} events from {} units", firstTick, currentTick, ticks, events.size(), units.size());
    }

    /**
     * Seed for one (tick, unit) pair. The three numbers are mixed with SplittableRandom on purpose:
     * java.util.Random started with neighbouring seeds (unit ids 1, 2, 3 ...) gives strongly correlated first
     * values, which made faults appear in bursts and left too few empty ticks.
     */
    private long tickSeed(LocalDateTime tick, long unitId) {
        return new SplittableRandom(props.seed()
                ^ (tick.toEpochSecond(ZoneOffset.UTC) * 0x9E3779B97F4A7C15L)
                ^ (unitId * 0xC2B2AE3D27D4EB4FL)).nextLong();
    }

    /**
     * The tick after the last one that already has events in the table. With an empty table
     * (seed not finished yet) only the current tick is played.
     */
    private LocalDateTime firstMissingTick(LocalDateTime currentTick) {
        LocalDateTime lastEvent = jdbc.queryForObject("select max(event_time) from event", LocalDateTime.class);
        if (lastEvent == null) {
            return currentTick;
        }
        LocalDateTime next = EventSimulator.tickOfEvent(lastEvent).plusMinutes(EventSimulator.TICK_MINUTES);
        LocalDateTime earliest = currentTick.minusDays(props.backfillDays());
        return next.isBefore(earliest) ? earliest : next;
    }

    private List<SimUnit> loadUnits() {
        return jdbc.query("""
                select u.id, u.creation_date, t.model_name, p.profile
                from unit u
                join unit_type t on t.id = u.unit_type_id
                join mock_unit_profile p on p.unit_id = u.id
                """,
                (rs, rowNum) -> new SimUnit(
                        rs.getLong("id"),
                        rs.getObject("creation_date", LocalDateTime.class),
                        faultsPerDayByModel.getOrDefault(rs.getString("model_name"), 0.0),
                        Profile.valueOf(rs.getString("profile"))));
    }
}
