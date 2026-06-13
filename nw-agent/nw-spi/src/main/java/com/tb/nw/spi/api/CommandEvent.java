package com.tb.nw.spi.api;

/**
 * An {@link NwEvent} that directs a target node's agent to do something.
 * Produced by the orchestrator ({@link FailoverPlanGenerator} → Dispatcher);
 * consumed by the receiving agent's ActionEndpoint → {@link FailoverAction}.
 *
 * <p>Every concrete command (e.g. {@code MySqlPromoteSlaveCommand}) implements
 * a plugin-specific intermediate marker ({@code MySqlCommandEvent}) plus this
 * one.</p>
 *
 * <p>The command's id ({@link #eventId}) is the idempotency key — the
 * receiving agent records "already executed" against this id so a retransmit
 * after a Dispatcher restart short-circuits to the cached result.</p>
 */
public interface CommandEvent extends NwEvent {

    @Override default EventType eventType() { return EventType.COMMAND; }

    /** What the receiving agent should do, e.g. {@code "promote-slave"}, {@code "fence-master"}. */
    String commandKind();

    /** Node id where the command must execute. */
    String targetNode();

    /** Failover epoch this command belongs to. Stale-epoch commands are refused at the endpoint. */
    long failoverEpoch();
}
