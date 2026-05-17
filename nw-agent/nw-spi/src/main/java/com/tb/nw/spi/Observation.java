package com.tb.nw.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Typed evidence record published by an investigator. One Observation per
 * (publisher, target, investigator) per tick. Written to the fabric under the
 * agent's lease so stale entries age out automatically.
 *
 * latencyNanos is stored as a long for clean JSON round-trips; convert with
 * Duration.ofNanos() / Duration.toNanos() when needed.
 */
public record Observation(
        String cluster,
        ClusterType clusterType,
        String target,
        String publisher,
        String investigator,
        Vantage vantage,
        HealthState state,
        long latencyNanos,
        Map<String, Object> detail,
        Instant freshness,
        int schemaVersion
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Duration latency() {
        return Duration.ofNanos(latencyNanos);
    }

    public static Observation of(String cluster, ClusterType clusterType,
                                 String target, String publisher,
                                 HealthCheck check, HealthReport report) {
        return new Observation(
                cluster,
                clusterType,
                target,
                publisher,
                check.id(),
                check.vantage(),
                report.state(),
                report.latency() == null ? 0L : report.latency().toNanos(),
                report.detail() == null ? Map.of() : report.detail(),
                report.freshness() == null ? Instant.now() : report.freshness(),
                CURRENT_SCHEMA_VERSION
        );
    }
}
