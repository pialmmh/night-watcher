package com.tb.nw.core.vote;

import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.core.cache.ObservationCache.ChangeEvent;
import com.tb.nw.core.cache.ObservationCache.ObsKey;
import com.tb.nw.core.coordinator.FailoverCoordinator;
import com.tb.nw.core.coordinator.events.MasterDeadDetected;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.Vantage;
import com.tb.nw.spi.api.Verdict;
import com.tb.nw.testkit.Fakes;
import com.tb.nw.testkit.TestPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureTrackerTest {

    static final String INVESTIGATOR = "test.client";

    /** Captures the listener so the test injects ChangeEvents by hand. */
    static class StubCache extends ObservationCache {
        Consumer<ChangeEvent> listener;
        final List<Observation<?>> data = new ArrayList<>();
        @Override public void addListener(Consumer<ChangeEvent> l) { this.listener = l; }
        @Override public Collection<Observation<?>> all() { return data; }
    }

    /** Coordinator that only records what would have fired. */
    static class CapturingCoordinator extends FailoverCoordinator {
        final List<MasterDeadDetected> fired = new ArrayList<>();
        @Override public void declareTargetDead(MasterDeadDetected event) { fired.add(event); }
    }

    /** Resolver whose verdict the test scripts directly. */
    static class ScriptedResolver extends Resolver {
        Optional<Verdict> verdict = Optional.empty();
        @Override public Optional<Verdict> verdictFor(String target) { return verdict; }
    }

    StubCache cache;
    CapturingCoordinator coordinator;
    ScriptedResolver resolver;
    FailureTracker tracker;

    void build(boolean requireOdown) {
        cache = new StubCache();
        coordinator = new CapturingCoordinator();
        resolver = new ScriptedResolver();
        tracker = new FailureTracker();
        tracker.cache = cache;
        tracker.cfg = Fakes.failoverConfig(List.of(INVESTIGATOR), requireOdown);
        tracker.coordinator = coordinator;
        tracker.resolver = resolver;
        tracker.init();
    }

    @BeforeEach void setUp() { build(false); }

    private void emit(HealthState state) { emit(state, INVESTIGATOR); }

    private void emit(HealthState state, String investigator) {
        Observation<?> o = TestPlugin.obs("c1", "node-x", "pub1", investigator,
                Vantage.CLIENT, state);
        cache.listener.accept(new ChangeEvent(ChangeEvent.Kind.UPDATED,
                new ObsKey("node-x", "pub1", investigator), o));
    }

    @Test void threeConsecutiveDeads_fireOnce() {
        emit(HealthState.DEAD); emit(HealthState.DEAD);
        assertTrue(coordinator.fired.isEmpty());
        emit(HealthState.DEAD);
        assertEquals(1, coordinator.fired.size());
        assertEquals("node-x", coordinator.fired.get(0).target());
        assertEquals(3, coordinator.fired.get(0).consecutiveFailures());
    }

    @Test void oneShot_counterRemovedAtThreshold() {
        emit(HealthState.DEAD); emit(HealthState.DEAD); emit(HealthState.DEAD);
        emit(HealthState.DEAD); emit(HealthState.DEAD);          // streak restarts: only 2
        assertEquals(1, coordinator.fired.size());
        emit(HealthState.DEAD);                                   // 3 again → second outage
        assertEquals(2, coordinator.fired.size());
    }

    @Test void fastResetsTheStreak() {
        emit(HealthState.DEAD); emit(HealthState.DEAD);
        emit(HealthState.FAST);
        emit(HealthState.DEAD); emit(HealthState.DEAD);
        assertTrue(coordinator.fired.isEmpty());
    }

    @Test void unknownIsSilence_neitherCountsNorResets() {
        emit(HealthState.DEAD); emit(HealthState.DEAD);
        emit(HealthState.UNKNOWN);
        emit(HealthState.DEAD);
        assertEquals(1, coordinator.fired.size());
    }

    @Test void nonTriggerInvestigator_neverFires() {
        emit(HealthState.DEAD, "host.uptime");
        emit(HealthState.DEAD, "host.uptime");
        emit(HealthState.DEAD, "host.uptime");
        assertTrue(coordinator.fired.isEmpty());
    }

    @Test void requireOdown_holdsOnSdown_thenFiresOnOdown() {
        build(true);
        resolver.verdict = Optional.of(Verdict.SDOWN);
        emit(HealthState.DEAD); emit(HealthState.DEAD); emit(HealthState.DEAD);
        assertTrue(coordinator.fired.isEmpty(), "held at SDOWN");

        // counter was KEPT while holding — the very next DEAD re-evaluates
        resolver.verdict = Optional.of(Verdict.ODOWN);
        emit(HealthState.DEAD);
        assertEquals(1, coordinator.fired.size());
    }

    @Test void requireOdown_noEvidence_holds() {
        build(true);
        resolver.verdict = Optional.empty();
        emit(HealthState.DEAD); emit(HealthState.DEAD); emit(HealthState.DEAD);
        assertTrue(coordinator.fired.isEmpty());
    }
}
