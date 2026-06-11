package com.tb.nw.spi;

/**
 * An {@link NwEvent} returned by the receiving agent after executing (or
 * refusing) a {@link CommandEvent}.
 *
 * <p>Correlated to the original command via {@link #inReplyToEventId} — the
 * Dispatcher matches results to in-flight commands on this field. The
 * {@link #ok} flag is the coarse signal; {@link #reason} carries the
 * specific check code (e.g. {@code "epoch-stale"}, {@code "role-not-permitted"})
 * when a command was refused, or a human description on success.</p>
 *
 * <p>Plugin-specific result fields go on concrete records, e.g.
 * {@code MySqlPromoteResult.newMasterReadOnly = false, secondsSpentPromoting = 8}.</p>
 */
public interface CommandResultEvent extends NwEvent {

    @Override default EventType eventType() { return EventType.COMMAND_RESULT; }

    /** The {@link CommandEvent#eventId} this is the result for. */
    String inReplyToEventId();

    /** Coarse outcome — true on success or idempotent-already-done. */
    boolean ok();

    /** Refusal code or success summary. Never null. */
    String reason();
}
