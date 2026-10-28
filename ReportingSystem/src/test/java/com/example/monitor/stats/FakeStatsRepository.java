package com.example.monitor.stats;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stands in for the database: keeps the state in a field and remembers every call.
 * Only the methods the tasklets use are replaced.
 */
class FakeStatsRepository extends StatsRepository {

    Optional<EtlState> state = Optional.empty();
    long maxEventId;
    boolean checkEventUnchanged = true;
    boolean dailyEmpty;
    boolean eventsExist = true;
    int removedUnits;

    int deleteAllCalls;
    final List<long[]> addedSlices = new ArrayList<>();
    final List<Long> savedStates = new ArrayList<>();

    FakeStatsRepository() {
        super(null, StatsProperties.defaults());
    }

    @Override
    public Optional<EtlState> loadState() {
        return state;
    }

    @Override
    public void saveState(long lastEventId, LocalDateTime now) {
        savedStates.add(lastEventId);
        state = Optional.of(new EtlState(lastEventId, lastEventId == 0 ? null : lastEventId, 1L, now));
    }

    @Override
    public long maxEventId() {
        return maxEventId;
    }

    @Override
    public boolean checkEventIsUnchanged(EtlState s) {
        return checkEventUnchanged;
    }

    @Override
    public boolean dailyTableIsEmpty() {
        return dailyEmpty;
    }

    @Override
    public boolean eventsExistUpTo(long eventId) {
        return eventsExist;
    }

    @Override
    public void deleteAllStats() {
        deleteAllCalls++;
        state = Optional.empty();
    }

    @Override
    public int deleteStatsOfRemovedUnits() {
        return removedUnits;
    }

    @Override
    public int addEventsToDaily(long fromId, long toId) {
        addedSlices.add(new long[] {fromId, toId});
        return 1;
    }
}
