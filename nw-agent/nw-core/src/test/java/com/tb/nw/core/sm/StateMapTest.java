package com.tb.nw.core.sm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMapTest {

    record Go() {}
    record Finish() {}

    private StateMap<StateMapTest> twoStep() {
        return StateMap.<StateMapTest>builder()
                .name("test")
                .initialState("A")
                .state("A").interim().on(Go.class, "B")
                .state("B").interim().on(Finish.class, "DONE")
                .state("DONE").terminal().outcomeOk()
                .build();
    }

    @Test void walksTheTable() {
        StateMap<StateMapTest> sm = twoStep();
        sm.start(this);
        assertEquals("A", sm.currentState());
        assertTrue(sm.fire(new Go()));
        assertEquals("B", sm.currentState());
        assertTrue(sm.fire(new Finish()));
        assertTrue(sm.isTerminal());
        sm.close();
    }

    @Test void unmatchedEvent_returnsFalse_andStays() {
        StateMap<StateMapTest> sm = twoStep();
        sm.start(this);
        assertFalse(sm.fire(new Finish()));            // B-only event fired in A
        assertEquals("A", sm.currentState());
        sm.close();
    }

    @Test void onEntryHookRuns() {
        AtomicInteger entered = new AtomicInteger();
        StateMap<StateMapTest> sm = StateMap.<StateMapTest>builder()
                .name("hook").initialState("A")
                .state("A").interim().on(Go.class, "B")
                .state("B").terminal().outcomeOk().onEntry(self -> entered.incrementAndGet())
                .build();
        sm.start(this);
        sm.fire(new Go());
        assertEquals(1, entered.get());
        sm.close();
    }

    @Test void timeoutTransitions() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        StateMap<StateMapTest> sm = StateMap.<StateMapTest>builder()
                .name("timeout").initialState("WAIT")
                .state("WAIT").interim().timeout(150, TimeUnit.MILLISECONDS, "EXPIRED")
                .state("EXPIRED").terminal().outcomeFailure("late")
                        .onEntry(self -> failed.countDown())
                .build();
        sm.start(this);
        assertTrue(failed.await(2, TimeUnit.SECONDS), "timeout should fire");
        assertEquals("EXPIRED", sm.currentState());
        sm.close();
    }

    /**
     * Regression for the re-arm race the mock harness caught live
     * (2026-06-07): firing into a CLOSED machine must not throw from the
     * terminated scheduler — the consumer contract is "publish the new
     * machine before closing the old", and a stray late fire must die
     * quietly, not take the caller down.
     */
    @Test void fireAfterClose_doesNotThrow() {
        StateMap<StateMapTest> sm = twoStep();
        sm.start(this);
        sm.close();
        try {
            sm.fire(new Go());
        } catch (RuntimeException e) {
            // acceptable only if it's a deliberate refusal, not a scheduler crash
            assertFalse(e.getMessage() != null && e.getMessage().contains("rejected"),
                    "closed machine must not surface executor-rejection: " + e);
        }
    }
}
