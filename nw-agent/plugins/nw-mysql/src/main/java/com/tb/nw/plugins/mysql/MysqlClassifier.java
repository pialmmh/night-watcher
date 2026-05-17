package com.tb.nw.plugins.mysql;

import com.tb.nw.spi.HealthReport;

import java.time.Duration;
import java.util.Map;

/** Pulls the "did the query return fast / degraded / dead" decision out of every probe. */
final class MysqlClassifier {

    private MysqlClassifier() {}

    static HealthReport classifyByLatency(Duration latency, Map<String, Object> detail, MysqlConfig cfg) {
        long ms = latency.toMillis();
        if (ms <= cfg.fastThresholdMs())    return HealthReport.fast(latency, detail);
        if (ms <= cfg.degradedThresholdMs()) return HealthReport.degraded(latency, detail);
        return HealthReport.dead(latency, detail);
    }
}
