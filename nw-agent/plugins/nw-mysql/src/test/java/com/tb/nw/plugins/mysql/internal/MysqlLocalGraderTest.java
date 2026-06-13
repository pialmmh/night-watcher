package com.tb.nw.plugins.mysql.internal;

import com.tb.nw.spi.api.HealthState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlLocalGraderTest {

    // thresholds for these cases: degraded at 500 ms, promotion lag window 10 s
    private static HealthState grade(Boolean readOnly, Boolean io, Boolean sql, Long behind) {
        return MysqlLocalGrader.grade(10, readOnly, io, sql, behind, 500, 10);
    }

    // ── Role 1: master self-watch ──

    @Test void masterReadWriteAndAliveIsFast() {
        assertEquals(HealthState.FAST, grade(false, null, null, null));
    }

    @Test void masterFlippedReadOnlyIsDegraded() {
        assertEquals(HealthState.DEGRADED, grade(true, null, null, null));
    }

    @Test void grossSlownessDropsMasterToDegraded() {
        assertEquals(HealthState.DEGRADED,
                MysqlLocalGrader.grade(900, false, null, null, null, 500, 10));
    }

    // ── Role 2: slave self-watch + promotability ──

    @Test void slaveCaughtUpIsFastAndPromotable() {
        assertEquals(HealthState.FAST, grade(true, true, true, 5L));
        assertTrue(MysqlLocalGrader.promotable(true, true, 5L, 10));
    }

    @Test void slaveLaggingBeyondWindowIsDegradedAndNotPromotable() {
        assertEquals(HealthState.DEGRADED, grade(true, true, true, 50L));
        assertFalse(MysqlLocalGrader.promotable(true, true, 50L, 10));
    }

    @Test void slaveWithStoppedThreadIsDegradedAndNotPromotable() {
        assertEquals(HealthState.DEGRADED, grade(true, false, true, 0L));
        assertEquals(HealthState.DEGRADED, grade(true, true, false, 0L));
        assertFalse(MysqlLocalGrader.promotable(false, true, 0L, 10));
    }

    @Test void slaveWithUnknownLagIsNotPromotable() {
        assertEquals(HealthState.DEGRADED, grade(true, true, true, null));
        assertFalse(MysqlLocalGrader.promotable(true, true, null, 10));
    }
}
