package com.tb.nw.core.boards.api;

import com.tb.nw.core.lifecycle.internal.TestLifecycle;
import com.tb.nw.testkit.FakeFabric;
import com.tb.nw.testkit.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailoverGateTest {

    FakeFabric fabric;

    private FailoverGate gate(String node) {
        FailoverGate g = new FailoverGate();
        g.cfg = Fakes.agentConfig(node, "c1");
        g.lease = TestLifecycle.lease(g.cfg, fabric);
        g.fabric = fabric;
        return g;
    }

    @BeforeEach void setUp() { fabric = new FakeFabric(); }

    @Test void firstAcquirerWins_secondStandsDown() {
        FailoverGate a = gate("agent-a");
        FailoverGate b = gate("agent-b");
        assertTrue(a.tryAcquire());
        assertFalse(b.tryAcquire());
        assertEquals("agent-a", b.holder().orElseThrow());
    }

    @Test void reacquireByHolder_isIdempotent() {
        FailoverGate a = gate("agent-a");
        assertTrue(a.tryAcquire());
        assertTrue(a.tryAcquire());
    }

    @Test void releaseFreesTheGate_forTheNextWinner() {
        FailoverGate a = gate("agent-a");
        FailoverGate b = gate("agent-b");
        assertTrue(a.tryAcquire());
        a.release();
        assertTrue(b.tryAcquire());
        assertEquals("agent-b", b.holder().orElseThrow());
    }

    @Test void releaseByNonHolder_doesNothing() {
        FailoverGate a = gate("agent-a");
        FailoverGate b = gate("agent-b");
        assertTrue(a.tryAcquire());
        b.release();                                   // only-if-mine: must not free a's claim
        assertEquals("agent-a", a.holder().orElseThrow());
        assertFalse(b.tryAcquire());
    }

    @Test void deadLeaderLosesTheClaim() {
        FailoverGate a = gate("agent-a");
        assertTrue(a.tryAcquire());
        fabric.expireLease(a.lease.current().id());    // agent-a dies
        assertTrue(gate("agent-b").tryAcquire());
    }

    @Test void epochStartsAtZero_andBumpsDurably() {
        FailoverGate a = gate("agent-a");
        assertEquals(0, a.epoch());
        assertEquals(1, a.bumpEpoch());
        assertEquals(2, a.bumpEpoch());
        assertEquals(2, gate("agent-b").epoch());      // durable, visible to everyone
    }
}
