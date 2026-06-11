package com.tb.nw.spi;

/**
 * An {@link NwEvent} produced by a probe ({@link HealthCheck}) — reports
 * the probe's classification of the target's state, plus whatever typed
 * detail fields the plugin needs.
 *
 * <p>Every {@link HealthCheck#probe} returns a record implementing this
 * (typically via a plugin-specific intermediate marker — {@code MySqlHealthEvent},
 * {@code PostgresHealthEvent}, etc.).</p>
 *
 * <p>The framework reads {@link #healthState} to drive coarse-grained
 * decisions (the 3-strike SDOWN counter, the Resolver's bucket-and-score
 * pipeline). Service-specific fields on the concrete record drive the
 * plugin's own logic (Aggregator, CandidateSelector, PlanGenerator).</p>
 */
public interface HealthCheckEvent extends NwEvent {

    @Override default EventType eventType() { return EventType.HEALTH_CHECK; }

    /** Coarse classification — drives the framework's failure-tracking math. */
    HealthState healthState();

    /** Node id / service id being probed (for cache keying + envelope identity). */
    String target();
}
