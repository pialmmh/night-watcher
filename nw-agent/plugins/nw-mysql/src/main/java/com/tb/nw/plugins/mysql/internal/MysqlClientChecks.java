package com.tb.nw.plugins.mysql.internal;

import com.tb.nw.spi.api.CheckResult;
import com.tb.nw.spi.api.HealthChecks;

/**
 * The check-set for the CLIENT canary — Role 3, expressed as one method per
 * check. Pure: the canary outcome (did it answer, did it return rows, how long
 * it took) comes in, a verdict comes out. The query throwing or exceeding the
 * dead threshold is handled by the probe itself (→ DEAD) before these run.
 */
public final class MysqlClientChecks {

    private MysqlClientChecks() {}

    public static HealthChecks.Verdict evaluate(boolean hasRow, long latencyMs,
                                                long fastThresholdMs, long deadThresholdMs) {
        return HealthChecks.builder()
                .check("returns-rows", () -> hasRow
                        ? CheckResult.up() : CheckResult.degraded("query returned no rows"))
                .check("latency", () -> {
                    if (latencyMs <= fastThresholdMs) return CheckResult.up();
                    if (latencyMs <= deadThresholdMs) return CheckResult.degraded("slow: " + latencyMs + "ms");
                    return CheckResult.down("over dead threshold: " + latencyMs + "ms");
                })
                .run();
    }
}
