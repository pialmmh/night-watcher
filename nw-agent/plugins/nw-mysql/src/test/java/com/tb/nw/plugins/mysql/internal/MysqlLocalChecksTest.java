package com.tb.nw.plugins.mysql.internal;

import com.tb.nw.spi.api.HealthChecks;
import com.tb.nw.spi.api.HealthState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlLocalChecksTest {

    // promotion lag window 10 s for all cases
    private static HealthState master(boolean serviceActive, boolean diskOk, Boolean readOnly) {
        return MysqlLocalChecks.evaluate(serviceActive, diskOk, readOnly, null, null, null, 10).state();
    }

    private static HealthChecks.Verdict slave(boolean serviceActive, boolean diskOk,
                                              Boolean io, Boolean sql, Long lag) {
        return MysqlLocalChecks.evaluate(serviceActive, diskOk, true, io, sql, lag, 10);
    }

    // ── Role 1: master self-watch ──

    @Test void masterReadWriteAndAliveIsUp() {
        assertEquals(HealthState.FAST, master(true, true, false));
    }

    @Test void masterGoneReadOnlyIsDegraded() {
        assertEquals(HealthState.DEGRADED, master(true, true, true));
    }

    // ── critical OS checks (down on fail), the worst-wins rule ──

    @Test void serviceInactiveIsDown() {
        assertEquals(HealthState.DEAD, master(false, true, false));
    }

    @Test void dataDiskFullIsDown() {
        assertEquals(HealthState.DEAD, master(true, false, false));
    }

    @Test void criticalDownBeatsDegraded() {
        // master read-only (degraded) AND disk full (down) -> down wins
        assertEquals(HealthState.DEAD, master(true, false, true));
    }

    // ── Role 2: slave self-watch + promotability ──

    @Test void slaveCaughtUpIsUp() {
        assertEquals(HealthState.FAST, slave(true, true, true, true, 5L).state());
    }

    @Test void slaveLaggingBeyondWindowIsDegraded() {
        assertEquals(HealthState.DEGRADED, slave(true, true, true, true, 50L).state());
    }

    @Test void slaveWithStoppedThreadIsDegraded() {
        assertEquals(HealthState.DEGRADED, slave(true, true, false, true, 0L).state());
        assertEquals(HealthState.DEGRADED, slave(true, true, true, false, 0L).state());
    }

    @Test void slaveUnknownLagIsDegraded() {
        assertEquals(HealthState.DEGRADED, slave(true, true, true, true, null).state());
    }

    @Test void verdictNamesTheFailingChecks() {
        HealthChecks.Verdict v = slave(true, true, false, true, 50L);
        assertFalse(v.failures().isEmpty());
        assertTrue(v.summary().contains("replica IO"));
    }
}
