package com.tb.nw.spi.api;

/**
 * An {@link NwEvent} representing a cross-actor inference — a verdict from
 * the Resolver, a failover-triggered signal from the FailureTracker, a
 * plan-completed notification from the Dispatcher.
 *
 * <p>Coordination events live inside the agent's own state-machine event
 * bus (consumed by {@code FailoverCoordinator} and the agent lifecycle SM).
 * They typically don't cross the network — they are framework / orchestrator
 * internal — but they still implement the {@link NwEvent} contract so logs
 * and traces are uniformly stamped.</p>
 */
public interface CoordinationEvent extends NwEvent {
    @Override default EventType eventType() { return EventType.COORDINATION; }
}
