package com.tb.nw.spi;

import java.time.Duration;

/**
 * Thin wrapper a probe returns: the typed {@link HealthCheckEvent} it minted,
 * plus the wall-clock latency the probe observed while gathering it.
 *
 * <p>The framework reads both — the event's {@link HealthCheckEvent#healthState}
 * drives the 3-strike SDOWN counter; the {@code latency} is exposed on the
 * {@link Observation} envelope for the Resolver's bucketing math and dashboards.</p>
 *
 * @param <T> the {@link HealthCheckEvent} subtype this report wraps
 */
public record HealthReport<T extends HealthCheckEvent>(
        T event,
        Duration latency
) {
    public HealthState state() { return event.healthState(); }

    public static <T extends HealthCheckEvent> HealthReport<T> of(T event, Duration latency) {
        return new HealthReport<>(event, latency);
    }
}
