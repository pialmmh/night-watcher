package com.tb.nw.spi;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of a single {@link HealthCheck#probe} invocation. The {@code detail}
 * is plugin-typed — every plugin defines its own {@link PluginEntity}
 * subtype carrying the fields downstream consumers (HealthAggregator,
 * FailoverPlanGenerator, FailoverAction) need. There is no untyped map.
 *
 * <p>For unknown / not-applicable cases the plugin must still supply a
 * non-null detail (typically a "no-signal" instance with zeroed fields and
 * a populated {@code errorMessage}-like field). The framework refuses to
 * accept a null detail because there would be no plugin-version stamp on it.</p>
 *
 * @param <T> plugin-specific detail type implementing {@link PluginEntity}
 */
public record HealthReport<T extends PluginEntity>(
        HealthState state,
        Duration latency,
        T detail,
        Instant freshness
) {
    public static <T extends PluginEntity> HealthReport<T> fast(Duration latency, T detail) {
        return new HealthReport<>(HealthState.FAST, latency, detail, Instant.now());
    }

    public static <T extends PluginEntity> HealthReport<T> degraded(Duration latency, T detail) {
        return new HealthReport<>(HealthState.DEGRADED, latency, detail, Instant.now());
    }

    public static <T extends PluginEntity> HealthReport<T> dead(Duration latency, T detail) {
        return new HealthReport<>(HealthState.DEAD, latency, detail, Instant.now());
    }

    public static <T extends PluginEntity> HealthReport<T> unknown(Duration latency, T detail) {
        return new HealthReport<>(HealthState.UNKNOWN, latency, detail, Instant.now());
    }
}
