package com.example.monitor.mock;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.monitor.mock.EventSimulator.MockEvent;

/** Bulk-inserts events into the event table. */
@Component
@ConditionalOnProperty(prefix = "mock", name = "enabled", havingValue = "true")
public class EventWriter {

    private static final String SQL =
            "insert into event (unit_id, event_time, type, placeholder, source) values (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;

    public EventWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(List<MockEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(SQL, events, 1000, (ps, e) -> {
            ps.setLong(1, e.unitId());
            ps.setObject(2, e.eventTime());
            ps.setString(3, e.type());
            ps.setString(4, e.placeholder());
            ps.setString(5, e.source());
        });
    }
}
