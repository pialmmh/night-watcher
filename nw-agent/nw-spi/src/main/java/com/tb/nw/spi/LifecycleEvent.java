package com.tb.nw.spi;

/**
 * An {@link NwEvent} representing an agent or state-machine transition —
 * fabric reachable / unreachable, facets detected, drain requested,
 * coordinator elected, plan loaded.
 *
 * <p>Lifecycle events are framework-internal; like {@link CoordinationEvent}
 * they typically don't cross the network. The marker exists so they share
 * the uniform {@code NwEvent} stamping with health-check and command events.</p>
 */
public interface LifecycleEvent extends NwEvent {
    @Override default EventType eventType() { return EventType.LIFECYCLE; }
}
