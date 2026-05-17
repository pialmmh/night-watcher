package com.tb.nw.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record HealthReport(
        HealthState state,
        Duration latency,
        Map<String, Object> detail,
        Instant freshness
) {
    public static HealthReport fast(Duration latency, Map<String, Object> detail) {
        return new HealthReport(HealthState.FAST, latency, detail, Instant.now());
    }

    public static HealthReport degraded(Duration latency, Map<String, Object> detail) {
        return new HealthReport(HealthState.DEGRADED, latency, detail, Instant.now());
    }

    public static HealthReport dead(Duration latency, Map<String, Object> detail) {
        return new HealthReport(HealthState.DEAD, latency, detail, Instant.now());
    }

    public static HealthReport unknown(String reason) {
        return new HealthReport(HealthState.UNKNOWN, Duration.ZERO,
                Map.of("reason", reason), Instant.now());
    }
}
