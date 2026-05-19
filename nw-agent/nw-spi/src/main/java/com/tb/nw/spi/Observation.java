package com.tb.nw.spi;

import java.time.Duration;
import java.time.Instant;

/**
 * Typed evidence record published by an investigator. One observation per
 * (publisher, target, investigator) per tick. Written to the fabric under the
 * agent's lease so stale entries age out automatically.
 *
 * <p>The {@code detail} field is plugin-typed: every plugin defines its own
 * {@link PluginEntity} record holding the fields that probe / aggregator /
 * action implementations actually use. There is no untyped map anywhere.</p>
 *
 * <p>{@code pluginId} and {@code pluginVersion} are stamped on the envelope
 * at construction time (read from {@code detail}). They appear at the top
 * level of the serialized JSON so the framework can validate them
 * <em>before</em> deserializing the typed body — protecting against
 * version-mismatch payloads. Anything whose {@code pluginVersion} does not
 * match a locally loaded plugin's descriptor is dropped at the cache layer.</p>
 *
 * <p>{@code latencyNanos} is a long for clean JSON round-tripping; convert
 * with {@code Duration.ofNanos()} / {@code Duration.toNanos()} when needed.</p>
 *
 * @param <T> plugin-specific detail type implementing {@link PluginEntity}
 */
public record Observation<T extends PluginEntity>(
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
        T detail
) implements PluginEntity {

    public Duration latency() {
        return Duration.ofNanos(latencyNanos);
    }

    /**
     * Factory that pulls {@code pluginId} / {@code pluginVersion} from the
     * detail so the envelope and the body are guaranteed consistent.
     */
    public static <T extends PluginEntity> Observation<T> of(
            String cluster, ClusterType clusterType,
            String target, String publisher,
            HealthCheck<T> check, HealthReport<T> report) {
        T detail = report.detail();
        if (detail == null) {
            throw new IllegalArgumentException(
                    "Observation.detail must not be null — every plugin must supply a typed detail (including UNKNOWN cases)");
        }
        return new Observation<>(
                cluster,
                clusterType,
                target,
                publisher,
                check.id(),
                check.vantage(),
                report.state(),
                report.latency() == null ? 0L : report.latency().toNanos(),
                report.freshness() == null ? Instant.now() : report.freshness(),
                detail.pluginId(),
                detail.pluginVersion(),
                detail
        );
    }
}
