package com.example.monitor.mock;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.example.monitor.recycler.RecyclerEvent;

/**
 * Pure Java (no Spring). Decides what the bill recycler (component RC) of one machine reports
 * during one 5-minute tick. Every event uses a name from {@link RecyclerEvent}.
 *
 * Three independent streams:
 * <ul>
 *   <li>faults: rate = model faults per day x profile multiplier; a jam is followed by JAM_RECOVERY</li>
 *   <li>customer transactions: a deposit (READ_NOTE, NOTE_STACKING, CREDIT_NOTE, then stored for recycling
 *       or sent to the cashbox; sometimes rejected) or a payout (DISPENSING, DISPENSED), now and then a
 *       service operation (float, emptying)</li>
 *   <li>warnings and state changes: cashbox removed/replaced, note held in bezel, reset, disabled ...</li>
 * </ul>
 * Only the fault stream produces FAULT events, so a unit with fault rate 0 never has a fault.
 */
public final class EventSimulator {

    public static final int TICK_MINUTES = 5;
    private static final double TICKS_PER_DAY = 24 * 60 / (double) TICK_MINUTES; // 288

    /** event.type of every recycler event (the family "event about a component"). */
    public static final String TYPE = "PLACEHOLDER";
    /** event.placeholder: the component id of the bill recycler. */
    public static final String PLACEHOLDER = "RC";

    /** Share of inserted notes a normal scanner accepts; the rest is rejected (NOTE_REJECTED). */
    public static final double NORMAL_ACCEPT_SHARE = 0.92;

    /**
     * The behaviour planted into a unit. The multiplier scales the model's normal fault rate;
     * the accept share is how many inserted notes the scanner accepts (the rest is rejected).
     */
    public enum Profile {
        NORMAL(1.0, NORMAL_ACCEPT_SHARE),
        BORDERLINE(2.5, NORMAL_ACCEPT_SHARE),
        BAD(8.0, NORMAL_ACCEPT_SHARE),
        BAD_ONE_COMPONENT(6.0, NORMAL_ACCEPT_SHARE),   // 80% of its faults are the same fault (source)
        NEW_FEW_FAULTS(2.0, NORMAL_ACCEPT_SHARE),      // created 3 days ago, so it has only a few faults in total
        DIRTY_SCANNER(1.0, 0.70),                      // normal faults, but rejects 30% of the notes instead of 8%
        SILENT(0.0, NORMAL_ACCEPT_SHARE);              // never reports anything

        private final double faultMultiplier;
        private final double acceptShare;

        Profile(double faultMultiplier, double acceptShare) {
            this.faultMultiplier = faultMultiplier;
            this.acceptShare = acceptShare;
        }

        public double faultMultiplier() {
            return faultMultiplier;
        }

        public double acceptShare() {
            return acceptShare;
        }
    }

    public record SimUnit(long id, LocalDateTime createdAt, double modelFaultsPerDay, Profile profile) {
    }

    public record MockEvent(long unitId, LocalDateTime eventTime, String type, String placeholder, String source) {
    }

    /** How many customer transactions and how many warnings/state changes a unit reports per day. */
    public record Rates(double transactionsPerDay, double statusEventsPerDay) {
    }

    private record Weighted(RecyclerEvent event, int weight) {
    }

    /** The faults a bill recycler has, weighted by how often each one happens compared with the others. */
    private static final List<Weighted> FAULTS = List.of(
            new Weighted(RecyclerEvent.SAFE_NOTE_JAM, 25),
            new Weighted(RecyclerEvent.JAMMED, 15),
            new Weighted(RecyclerEvent.UNSAFE_NOTE_JAM, 10),
            new Weighted(RecyclerEvent.ERROR_DURING_PAYOUT, 10),
            new Weighted(RecyclerEvent.INCOMPLETE_PAYOUT, 10),
            new Weighted(RecyclerEvent.TIME_OUT, 10),
            new Weighted(RecyclerEvent.PAYOUT_OUT_OF_SERVICE, 6),
            new Weighted(RecyclerEvent.FAIL, 5),
            new Weighted(RecyclerEvent.SOFTWARE_ERROR, 5),
            new Weighted(RecyclerEvent.INCOMPLETE_FLOAT, 4));

    /** Warnings and state changes (never faults). */
    private static final List<Weighted> STATUS = List.of(
            new Weighted(RecyclerEvent.NOTE_HELD_IN_BEZEL, 15),
            new Weighted(RecyclerEvent.CASHBOX_REMOVED, 10),
            new Weighted(RecyclerEvent.CASHBOX_REPLACED, 10),
            new Weighted(RecyclerEvent.DISABLED, 8),
            new Weighted(RecyclerEvent.NOTE_PATH_OPEN, 6),
            new Weighted(RecyclerEvent.STACKER_FULL, 5),
            new Weighted(RecyclerEvent.INITIALISING, 5),
            new Weighted(RecyclerEvent.SLAVE_RESET, 5),
            new Weighted(RecyclerEvent.CHANNEL_DISABLE, 4),
            new Weighted(RecyclerEvent.FRAUD_ATTEMPT, 3),
            new Weighted(RecyclerEvent.NOTE_CLEARED_FROM_FRONT, 3),
            new Weighted(RecyclerEvent.NOTE_CLEARED_TO_CASHBOX, 3),
            new Weighted(RecyclerEvent.NOTE_FLOAT_REMOVED, 2),
            new Weighted(RecyclerEvent.NOTE_FLOAT_ATTACHED, 2),
            new Weighted(RecyclerEvent.NOTE_PAID_INTO_STACKER_AT_POWER_UP, 2),
            new Weighted(RecyclerEvent.NOTE_PAID_INTO_STORE_AT_POWER_UP, 2),
            new Weighted(RecyclerEvent.NOTE_DISPENSED_AT_POWER_UP, 2),
            new Weighted(RecyclerEvent.KEY_NOT_SET, 1));

    private static final List<RecyclerEvent> JAMS = List.of(
            RecyclerEvent.JAMMED, RecyclerEvent.SAFE_NOTE_JAM, RecyclerEvent.UNSAFE_NOTE_JAM);

    private EventSimulator() {
    }

    /** The latest 5-minute boundary that is not in the future, e.g. 12:07 gives 12:05. */
    public static LocalDateTime currentTick(LocalDateTime now) {
        LocalDateTime t = now.truncatedTo(ChronoUnit.MINUTES);
        return t.minusMinutes(t.getMinute() % TICK_MINUTES);
    }

    /** The tick in which an event with this time was reported, e.g. 12:03:20 gives 12:05. */
    public static LocalDateTime tickOfEvent(LocalDateTime eventTime) {
        return currentTick(eventTime).plusMinutes(TICK_MINUTES);
    }

    /**
     * What the recycler of the unit reports in the 5 minutes that end at {@code tick}.
     * Every event time is inside that window [tick - 5 min, tick).
     */
    public static List<MockEvent> simulateTick(SimUnit unit, LocalDateTime tick, Random rng, Rates rates) {
        if (unit.profile() == Profile.SILENT || !tick.isAfter(unit.createdAt())) {
            return List.of();
        }
        List<MockEvent> out = new ArrayList<>();

        double pFault = Math.min(1.0, unit.modelFaultsPerDay() * unit.profile().faultMultiplier() / TICKS_PER_DAY);
        if (rng.nextDouble() < pFault) {
            addFault(unit, tick, rng, out);
        }
        int transactions = count(rng, rates.transactionsPerDay() / TICKS_PER_DAY);
        for (int i = 0; i < transactions; i++) {
            addTransaction(unit, tick, rng, out);
        }
        int statusEvents = count(rng, rates.statusEventsPerDay() / TICKS_PER_DAY);
        for (int i = 0; i < statusEvents; i++) {
            out.add(event(unit, tick.minusSeconds(1 + rng.nextInt(TICK_MINUTES * 60)), pick(STATUS, rng)));
        }
        return out;
    }

    /** A jam is followed a few seconds later by JAM_RECOVERY (a warning, not a second fault). */
    private static void addFault(SimUnit unit, LocalDateTime tick, Random rng, List<MockEvent> out) {
        RecyclerEvent fault = pickFault(unit, rng);
        LocalDateTime when = tick.minusSeconds(10 + rng.nextInt(TICK_MINUTES * 60 - 10));
        out.add(event(unit, when, fault));
        if (JAMS.contains(fault)) {
            out.add(event(unit, when.plusSeconds(2 + rng.nextInt(7)), RecyclerEvent.JAM_RECOVERY));
        }
    }

    private static RecyclerEvent pickFault(SimUnit unit, Random rng) {
        if (unit.profile() == Profile.BAD_ONE_COMPONENT && rng.nextDouble() < 0.8) {
            return FAULTS.get((int) (unit.id() % FAULTS.size())).event();
        }
        return pick(FAULTS, rng);
    }

    /** One customer or service transaction: a short sequence of OPERATION events, 1-3 seconds apart. */
    private static void addTransaction(SimUnit unit, LocalDateTime tick, Random rng, List<MockEvent> out) {
        List<RecyclerEvent> steps = new ArrayList<>(4);
        double kind = rng.nextDouble();
        if (kind < 0.68) {                                   // a customer pays in a note
            steps.add(RecyclerEvent.READ_NOTE);
            if (rng.nextDouble() < unit.profile().acceptShare()) {
                steps.add(RecyclerEvent.NOTE_STACKING);
                steps.add(RecyclerEvent.CREDIT_NOTE);
                // the recycler keeps most notes for change; the rest goes to the cashbox
                steps.add(rng.nextDouble() < 0.6 ? RecyclerEvent.NOTE_STORED_IN_PAYOUT : RecyclerEvent.NOTE_STACKED);
            } else {
                steps.add(RecyclerEvent.NOTE_REJECTING);
                steps.add(RecyclerEvent.NOTE_REJECTED);
            }
        } else if (kind < 0.98) {                            // change is paid out
            steps.add(RecyclerEvent.DISPENSING);
            steps.add(rng.nextDouble() < 0.02 ? RecyclerEvent.HALTED : RecyclerEvent.DISPENSED);
        } else {                                             // a service operation
            switch (rng.nextInt(4)) {
                case 0 -> steps.addAll(List.of(RecyclerEvent.FLOATING, RecyclerEvent.CASHBOX_PAID, RecyclerEvent.FLOATED));
                case 1 -> steps.addAll(List.of(RecyclerEvent.EMPTYING, RecyclerEvent.EMPTIED));
                case 2 -> steps.addAll(List.of(RecyclerEvent.SMART_EMPTYING, RecyclerEvent.SMART_EMPTIED));
                default -> steps.add(RecyclerEvent.NOTE_TRANSFERED_TO_STACKER);
            }
        }
        // start at least 15 s before the tick, so 4 steps of at most 3 s stay inside the window
        LocalDateTime when = tick.minusSeconds(15 + rng.nextInt(TICK_MINUTES * 60 - 15));
        for (RecyclerEvent step : steps) {
            out.add(event(unit, when, step));
            when = when.plusSeconds(1 + rng.nextInt(3));
        }
    }

    /** Whole part of the expected number, plus one more with the probability of the fraction. */
    private static int count(Random rng, double expected) {
        int n = (int) expected;
        return rng.nextDouble() < expected - n ? n + 1 : n;
    }

    private static RecyclerEvent pick(List<Weighted> options, Random rng) {
        int total = 0;
        for (Weighted w : options) {
            total += w.weight();
        }
        int r = rng.nextInt(total);
        for (Weighted w : options) {
            r -= w.weight();
            if (r < 0) {
                return w.event();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static MockEvent event(SimUnit unit, LocalDateTime when, RecyclerEvent e) {
        return new MockEvent(unit.id(), when, TYPE, PLACEHOLDER, e.source());
    }
}
