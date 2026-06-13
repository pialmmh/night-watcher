package com.tb.nw.spi.api;

import java.time.Duration;
import java.time.Instant;

/**
 * Wire envelope a probe's {@link HealthCheckEvent} travels in across the
 * etcd fabric. Carries framework metadata (cluster, target, publisher) +
 * the typed event itself.
 *
 * <p>{@code pluginId} and {@code pluginVersion} live at the envelope level
 * (copied from the event at construction) so the framework can validate
 * them <em>before</em> deserializing the typed body — protecting against
 * version-mismatch payloads.</p>
 *
 * <p>{@code latencyNanos} is a long for clean JSON round-tripping; convert
 * with {@code Duration.ofNanos()} when needed.</p>
 *
 * @param <T> plugin-specific {@link HealthCheckEvent} subtype
 */
public record Observation<T extends HealthCheckEvent>(
        String cluster,
        ClusterType clusterType,
        String target,
        String publisher,
        String investigator,
        Vantage vantage,
        HealthState state,
        long latencyNanos,
        Instant freshness,
        String pluginId,
        String pluginVersion,
        T event
) implements NwEvent {

    /** Envelope event id mirrors the wrapped event id — same physical event. */
    @Override public String eventId() { return event.eventId(); }

    /** Envelope timestamp == event freshness, but exposed via NwEvent contract. */
    @Override public Instant timestamp() { return freshness; }

    @Override public EventType eventType() { return EventType.HEALTH_CHECK; }

    public Duration latency() {
        return Duration.ofNanos(latencyNanos);
    }

    /**
     * Factory that pulls plugin id / version / state / event id from the
     * event so the envelope and the body are guaranteed consistent.
     */
    public static <T extends HealthCheckEvent> Observation<T> of(
            String cluster, ClusterType clusterType,
            String target, String publisher,
            HealthCheck<T> check, HealthReport<T> report) {
        T event = report.event();
        if (event == null) {
            throw new IllegalArgumentException(
                    "Observation.event must not be null — every probe must supply a typed HealthCheckEvent (including UNKNOWN cases)");
        }
        return new Observation<>(
                cluster,
                clusterType,
                target,
                publisher,
                check.id(),
                check.vantage(),
                event.healthState(),
                report.latency() == null ? 0L : report.latency().toNanos(),
                event.timestamp() == null ? Instant.now() : event.timestamp(),
                event.pluginId(),
                event.pluginVersion(),
                event
        );
    }
}
