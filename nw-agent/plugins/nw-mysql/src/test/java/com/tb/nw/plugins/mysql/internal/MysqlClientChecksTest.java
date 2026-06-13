package com.tb.nw.plugins.mysql.internal;

import com.tb.nw.spi.api.HealthState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlClientChecksTest {

    // fast threshold 50 ms, dead threshold 5000 ms
    private static HealthState grade(boolean hasRow, long latencyMs) {
        return MysqlClientChecks.evaluate(hasRow, latencyMs, 50, 5000).state();
    }

    @Test void fastAnswerWithRowsIsUp() {
        assertEquals(HealthState.FAST, grade(true, 20));
    }

    @Test void noRowsIsDegraded() {
        assertEquals(HealthState.DEGRADED, grade(false, 20));
    }

    @Test void slowButAnsweredIsDegraded() {
        assertEquals(HealthState.DEGRADED, grade(true, 500));
    }

    @Test void overDeadThresholdIsDown() {
        assertEquals(HealthState.DEAD, grade(true, 6000));
    }
}
