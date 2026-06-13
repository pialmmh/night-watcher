package com.tb.nw.spi.api;

/**
 * Coarse classification of every {@link NwEvent} crossing a plugin boundary.
 *
 * <p>Plugin authors don't choose this directly — they implement the
 * matching marker sub-interface ({@link HealthCheckEvent},
 * {@link CommandEvent}, etc.) and the type field is fixed in the marker's
 * default method.</p>
 */
public enum EventType {

    /** A probe finished and reports a state. Implement {@link HealthCheckEvent}. */
    HEALTH_CHECK,

    /** A directive from the orchestrator to a target node. Implement {@link CommandEvent}. */
    COMMAND,

    /** A target node's outcome for a previously dispatched command. Implement {@link CommandResultEvent}. */
    COMMAND_RESULT,

    /** A cross-actor inference / decision (verdict, failover trigger). Implement {@link CoordinationEvent}. */
    COORDINATION,

    /** Agent / state-machine state transition. Implement {@link LifecycleEvent}. */
    LIFECYCLE,

    /** Periodic structured metric (Prometheus-style). Reserved for future plugins. */
    METRIC
}
