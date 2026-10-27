package com.example.monitor.mock;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.monitor.mock.EventSimulator.MockEvent;
import com.example.monitor.mock.EventSimulator.Profile;
import com.example.monitor.mock.EventSimulator.SimUnit;
import com.example.monitor.mock.MockProperties.ModelRule;

/**
 * Runs once at startup. If the unit table is empty it creates the reference data
 * (models, addresses, locations, units) and fills the past {@code backfillDays} with events,
 * so the statistics have history to work on. After that MockEventScheduler keeps adding events.
 */
@Component
@ConditionalOnProperty(prefix = "mock", name = "enabled", havingValue = "true")
public class MockDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockDataSeeder.class);

    private static final String[][] CITIES = {
            {"Belgrade", "RS"}, {"Amsterdam", "NL"}, {"Berlin", "DE"}, {"Vienna", "AT"}, {"Zagreb", "HR"}
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MockProperties props;
    private final EventWriter writer;

    public MockDataSeeder(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                          MockProperties props, EventWriter writer) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.props = props;
        this.writer = writer;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from unit", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Mock data already present ({} units), skipping seed", existing);
            return;
        }
        tx.executeWithoutResult(status -> seed());
    }

    private void seed() {
        validate();
        // Rows left over from a previous run (e.g. after re-creating the tables) would clash with the new unit ids
        jdbc.update("delete from mock_unit_profile");
        Random rng = new Random(props.seed());
        LocalDateTime lastTick = EventSimulator.currentTick(LocalDateTime.now(ZoneOffset.UTC));

        // 1. Models, addresses, locations
        List<ModelRule> models = props.models();
        List<Long> modelIds = new ArrayList<>();
        for (ModelRule m : models) {
            modelIds.add(insert("unit_type", Map.<String, Object>of("model_name", m.name())));
        }
        List<Long> locationIds = new ArrayList<>();
        for (String[] city : CITIES) {
            long addressId = insert("address", Map.<String, Object>of("city", city[0], "country_code", city[1]));
            locationIds.add(insert("location",
                    Map.<String, Object>of("name", "Branch " + city[0], "address_id", addressId)));
        }

        // 2. Units, each with a planted profile (the answer key)
        List<Profile> profiles = buildProfiles(rng);
        List<SimUnit> units = new ArrayList<>();
        for (int i = 0; i < props.unitCount(); i++) {
            int modelIndex = i % models.size();
            Profile profile = profiles.get(i);
            LocalDateTime createdAt = profile == Profile.NEW_FEW_FAULTS
                    ? lastTick.minusDays(3)
                    : lastTick.minusDays(props.backfillDays() + 30 + rng.nextInt(300));

            long unitId = insert("unit", Map.<String, Object>of(
                    "serial_number", String.format("MCH-%05d", 1000 + i),
                    "unit_type_id", modelIds.get(modelIndex),
                    "location_id", locationIds.get(rng.nextInt(locationIds.size())),
                    "creation_date", createdAt));
            jdbc.update("insert into mock_unit_profile (unit_id, profile) values (?, ?)", unitId, profile.name());
            units.add(new SimUnit(unitId, createdAt, models.get(modelIndex).faultsPerDay(), profile));
        }

        // 3. History: replay every 5-minute tick of the past backfillDays for every unit
        LocalDateTime firstTick = lastTick.minusDays(props.backfillDays());
        List<MockEvent> events = new ArrayList<>();
        for (SimUnit unit : units) {
            Random unitRng = new Random(props.seed() * 1_000_003L + unit.id());
            for (LocalDateTime tick = firstTick.plusMinutes(EventSimulator.TICK_MINUTES);
                 !tick.isAfter(lastTick);
                 tick = tick.plusMinutes(EventSimulator.TICK_MINUTES)) {
                events.addAll(EventSimulator.simulateTick(unit, tick, unitRng, props.rates()));
            }
        }
        // Sort by time so event ids grow with time, like the real receive order
        events.sort(Comparator.comparing(MockEvent::eventTime));
        writer.saveAll(events);

        log.info("Mock data seeded: {} units, {} events. Answer key is in table mock_unit_profile.",
                units.size(), events.size());
    }

    private List<Profile> buildProfiles(Random rng) {
        MockProperties.Planted p = props.planted();
        List<Profile> list = new ArrayList<>();
        add(list, Profile.BAD, p.bad());
        add(list, Profile.BAD_ONE_COMPONENT, p.badOneComponent());
        add(list, Profile.BORDERLINE, p.borderline());
        add(list, Profile.NEW_FEW_FAULTS, p.newFewFaults());
        add(list, Profile.SILENT, p.silent());
        add(list, Profile.DIRTY_SCANNER, p.dirtyScanner());
        while (list.size() < props.unitCount()) {
            list.add(Profile.NORMAL);
        }
        Collections.shuffle(list, rng);
        return list;
    }

    private static void add(List<Profile> list, Profile profile, int count) {
        for (int i = 0; i < count; i++) {
            list.add(profile);
        }
    }

    private void validate() {
        if (props.models() == null || props.models().isEmpty()) {
            throw new IllegalStateException("mock.models must contain at least one model");
        }
        if (props.unitCount() <= 0) {
            throw new IllegalStateException("mock.unit-count must be positive");
        }
        if (props.backfillDays() <= 0) {
            throw new IllegalStateException("mock.backfill-days must be positive");
        }
        if (props.transactionsPerDay() < 0) {
            throw new IllegalStateException("mock.transactions-per-day must not be negative");
        }
        if (props.statusEventsPerDay() < 0) {
            throw new IllegalStateException("mock.status-events-per-day must not be negative");
        }
        if (props.planted().total() > props.unitCount()) {
            throw new IllegalStateException("mock.planted adds up to more than mock.unit-count");
        }
    }

    /**
     * Plain INSERT with an explicit column list. SimpleJdbcInsert is avoided on purpose: it reads the table's
     * columns from the JDBC metadata, and MySQL can return columns of a same-named table from ANOTHER database
     * on the server (e.g. "Unknown column 'idaddress'").
     */
    private long insert(String table, Map<String, Object> values) {
        List<String> columns = new ArrayList<>(values.keySet());
        String sql = "insert into " + table + " (" + String.join(", ", columns) + ") values ("
                + columns.stream().map(c -> "?").collect(Collectors.joining(", ")) + ")";
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < columns.size(); i++) {
                ps.setObject(i + 1, values.get(columns.get(i)));
            }
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey(), "no generated id for " + table).longValue();
    }
}
