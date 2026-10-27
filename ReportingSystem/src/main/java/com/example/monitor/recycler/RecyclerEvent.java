package com.example.monitor.recycler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every event a note recycler can report: the generic recycler event list (codes 176-250).
 * The same 58 rows are in the database table event_source (db/schema.sql); keep both lists equal.
 *
 * {@link #source()} is the exact text stored in event.source. It is not always the Java name:
 * two names contain a hyphen ("..._AT_POWER-UP"), which a Java name cannot have.
 * A few events only exist on coin or ticket devices; they are listed for completeness,
 * the mock generator never sends them for the note recycler.
 */
public enum RecyclerEvent {

    JAM_RECOVERY(176, "JAM_RECOVERY", EventCategory.WARNING, "The unit is recovering from a jam"),
    ERROR_DURING_PAYOUT(177, "ERROR_DURING_PAYOUT", EventCategory.FAULT, "An error stopped a payout"),
    SMART_EMPTYING(179, "SMART_EMPTYING", EventCategory.OPERATION, "Smart emptying of the payout to the cashbox is running"),
    SMART_EMPTIED(180, "SMART_EMPTIED", EventCategory.OPERATION, "Smart emptying finished"),
    CHANNEL_DISABLE(181, "CHANNEL_DISABLE", EventCategory.STATUS, "All note channels are disabled by the host"),
    INITIALISING(182, "INITIALISING", EventCategory.STATUS, "The unit is starting up"),
    COIN_MECH_ERROR(183, "COIN_MECH_ERROR", EventCategory.FAULT, "Coin mechanism error (coin devices only)"),
    EMPTYING(194, "EMPTYING", EventCategory.OPERATION, "Emptying of the payout to the cashbox is running"),
    EMPTIED(195, "EMPTIED", EventCategory.OPERATION, "Emptying finished"),
    COIN_MECH_JAMMED(196, "COIN_MECH_JAMMED", EventCategory.FAULT, "Coin mechanism jammed (coin devices only)"),
    COIN_MECH_RETURN_PRESSED(197, "COIN_MECH_RETURN_PRESSED", EventCategory.OPERATION, "Coin return button pressed (coin devices only)"),
    PAYOUT_OUT_OF_SERVICE(198, "PAYOUT_OUT_OF_SERVICE", EventCategory.FAULT, "The payout (recycler) part is out of service"),
    NOTE_FLOAT_REMOVED(199, "NOTE_FLOAT_REMOVED", EventCategory.WARNING, "The note float (recycler module) was removed"),
    NOTE_FLOAT_ATTACHED(200, "NOTE_FLOAT_ATTACHED", EventCategory.STATUS, "The note float (recycler module) was attached"),
    NOTE_TRANSFERED_TO_STACKER(201, "NOTE_TRANSFERED_TO_STACKER", EventCategory.OPERATION, "A note was moved from the payout to the cashbox"),
    NOTE_PAID_INTO_STACKER_AT_POWER_UP(202, "NOTE_PAID_INTO_STACKER_AT_POWER-UP", EventCategory.STATUS, "A note found in the path at power-up went to the cashbox"),
    NOTE_PAID_INTO_STORE_AT_POWER_UP(203, "NOTE_PAID_INTO_STORE_AT_POWER-UP", EventCategory.STATUS, "A note found in the path at power-up went to the payout store"),
    NOTE_STACKING(204, "NOTE_STACKING", EventCategory.OPERATION, "A note is being moved to its storage"),
    NOTE_DISPENSED_AT_POWER_UP(205, "NOTE_DISPENSED_AT_POWER-UP", EventCategory.STATUS, "A note found in the path at power-up was given back"),
    NOTE_HELD_IN_BEZEL(206, "NOTE_HELD_IN_BEZEL", EventCategory.WARNING, "A paid-out note waits in the bezel for the customer"),
    BAR_CODE_TICKET_ACKNOWLEDGE(209, "BAR_CODE_TICKET_ACKNOWLEDGE", EventCategory.OPERATION, "A bar code ticket was accepted (ticket devices only)"),
    DISPENSED(210, "DISPENSED", EventCategory.OPERATION, "A payout finished"),
    JAMMED(213, "JAMMED", EventCategory.FAULT, "A note is jammed"),
    HALTED(214, "HALTED", EventCategory.OPERATION, "A payout was stopped by the host"),
    FLOATING(215, "FLOATING", EventCategory.OPERATION, "Floating (moving notes to reach a target level) is running"),
    FLOATED(216, "FLOATED", EventCategory.OPERATION, "Floating finished"),
    TIME_OUT(217, "TIME_OUT", EventCategory.FAULT, "A payout timed out (a note could not be found)"),
    DISPENSING(218, "DISPENSING", EventCategory.OPERATION, "A payout is running"),
    NOTE_STORED_IN_PAYOUT(219, "NOTE_STORED_IN_PAYOUT", EventCategory.OPERATION, "An accepted note was stored for recycling"),
    INCOMPLETE_PAYOUT(220, "INCOMPLETE_PAYOUT", EventCategory.FAULT, "A payout ended with less money than requested"),
    INCOMPLETE_FLOAT(221, "INCOMPLETE_FLOAT", EventCategory.FAULT, "Floating ended before the target was reached"),
    CASHBOX_PAID(222, "CASHBOX_PAID", EventCategory.OPERATION, "Notes were moved to the cashbox during a payout or float"),
    COIN_CREDIT(223, "COIN_CREDIT", EventCategory.OPERATION, "A coin was credited (coin devices only)"),
    NOTE_PATH_OPEN(224, "NOTE_PATH_OPEN", EventCategory.WARNING, "The note path is open"),
    NOTE_CLEARED_FROM_FRONT(225, "NOTE_CLEARED_FROM_FRONT", EventCategory.STATUS, "A note found at power-up was rejected to the front"),
    NOTE_CLEARED_TO_CASHBOX(226, "NOTE_CLEARED_TO_CASHBOX", EventCategory.STATUS, "A note found at power-up was moved to the cashbox"),
    CASHBOX_REMOVED(227, "CASHBOX_REMOVED", EventCategory.WARNING, "The cashbox was removed"),
    CASHBOX_REPLACED(228, "CASHBOX_REPLACED", EventCategory.STATUS, "The cashbox was put back"),
    BAR_CODE_TICKET_VALIDATED(229, "BAR_CODE_TICKET_VALIDATED", EventCategory.OPERATION, "A bar code ticket was read (ticket devices only)"),
    FRAUD_ATTEMPT(230, "FRAUD_ATTEMPT", EventCategory.WARNING, "A fraud attempt was detected"),
    STACKER_FULL(231, "STACKER_FULL", EventCategory.WARNING, "The cashbox (stacker) is full"),
    DISABLED(232, "DISABLED", EventCategory.STATUS, "The unit is disabled"),
    UNSAFE_NOTE_JAM(233, "UNSAFE_NOTE_JAM", EventCategory.FAULT, "A note is jammed where the customer can reach it"),
    SAFE_NOTE_JAM(234, "SAFE_NOTE_JAM", EventCategory.FAULT, "A note is jammed inside the unit, out of the customer's reach"),
    NOTE_STACKED(235, "NOTE_STACKED", EventCategory.OPERATION, "A note reached the cashbox"),
    NOTE_REJECTED(236, "NOTE_REJECTED", EventCategory.OPERATION, "A note was refused and given back"),
    NOTE_REJECTING(237, "NOTE_REJECTING", EventCategory.OPERATION, "A note is being refused"),
    CREDIT_NOTE(238, "CREDIT_NOTE", EventCategory.OPERATION, "An accepted note was credited"),
    READ_NOTE(239, "READ_NOTE", EventCategory.OPERATION, "A note is being read"),
    OK(240, "OK", EventCategory.COMMAND, "Reply: command done"),
    SLAVE_RESET(241, "SLAVE_RESET", EventCategory.STATUS, "The unit restarted"),
    COMMAND_NOT_KNOWN(242, "COMMAND_NOT_KNOWN", EventCategory.COMMAND, "Reply: unknown command"),
    WRONG_NO_PARAMETERS(243, "WRONG_NO_PARAMETERS", EventCategory.COMMAND, "Reply: wrong number of parameters"),
    PARAMETER_OUT_OF_RANGE(244, "PARAMETER_OUT_OF_RANGE", EventCategory.COMMAND, "Reply: parameter out of range"),
    COMMAND_CANNOT_BE_PROCESSED(245, "COMMAND_CANNOT_BE_PROCESSED", EventCategory.COMMAND, "Reply: command cannot be processed now"),
    SOFTWARE_ERROR(246, "SOFTWARE_ERROR", EventCategory.FAULT, "Software error in the unit"),
    FAIL(248, "FAIL", EventCategory.FAULT, "General failure"),
    KEY_NOT_SET(250, "KEY_NOT_SET", EventCategory.WARNING, "The encryption key is not set");

    private static final Map<String, RecyclerEvent> BY_SOURCE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RecyclerEvent::source, Function.identity()));

    private final int code;
    private final String source;
    private final EventCategory category;
    private final String description;

    RecyclerEvent(int code, String source, EventCategory category, String description) {
        this.code = code;
        this.source = source;
        this.category = category;
        this.description = description;
    }

    /** Number of the event in the recycler protocol (176-250). */
    public int code() {
        return code;
    }

    /** The text stored in event.source. */
    public String source() {
        return source;
    }

    public EventCategory category() {
        return category;
    }

    public String description() {
        return description;
    }

    public boolean isFault() {
        return category == EventCategory.FAULT;
    }

    /** The event with this event.source text, if there is one. */
    public static Optional<RecyclerEvent> fromSource(String source) {
        return Optional.ofNullable(BY_SOURCE.get(source));
    }

    /** event.source of every FAULT event, in code order: the same list as stats.fault-sources. */
    public static List<String> faultSources() {
        return Arrays.stream(values()).filter(RecyclerEvent::isFault).map(RecyclerEvent::source).toList();
    }
}
