package com.tb.nw.spi.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthChecksTest {

    @Test void allChecksPassIsUp() {
        HealthChecks.Verdict v = HealthChecks.builder()
                .check("a", CheckResult::up)
                .check("b", CheckResult::up)
                .run();
        assertEquals(HealthState.FAST, v.state());
        assertTrue(v.up());
        assertTrue(v.failures().isEmpty());
    }

    @Test void oneDegradingCheckFailsIsDegraded() {
        HealthChecks.Verdict v = HealthChecks.builder()
                .check("a", CheckResult::up)
                .check("b", () -> CheckResult.degraded("slow"))
                .run();
        assertEquals(HealthState.DEGRADED, v.state());
        assertEquals(List.of("b: slow"), v.failures());
    }

    @Test void oneCriticalCheckBeatsManyDegraded() {
        HealthChecks.Verdict v = HealthChecks.builder()
                .check("a", () -> CheckResult.degraded("x"))
                .check("disk", () -> CheckResult.down("disk full"))
                .check("c", () -> CheckResult.degraded("y"))
                .run();
        assertEquals(HealthState.DEAD, v.state());
    }

    @Test void aThrowingCheckCountsAsDown() {
        HealthChecks.Verdict v = HealthChecks.builder()
                .check("boom", () -> { throw new RuntimeException("kaboom"); })
                .run();
        assertEquals(HealthState.DEAD, v.state());
        assertTrue(v.summary().contains("kaboom"));
    }

    @Test void noChecksIsUnknown() {
        assertEquals(HealthState.UNKNOWN, HealthChecks.builder().run().state());
    }
}
