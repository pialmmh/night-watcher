package com.tb.nw.core.vote;

import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.Vantage;
import com.tb.nw.spi.Verdict;
import com.tb.nw.testkit.Fakes;
import com.tb.nw.testkit.TestPlugin;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.tb.nw.spi.HealthState.DEAD;
import static com.tb.nw.spi.HealthState.DEGRADED;
import static com.tb.nw.spi.HealthState.FAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolverTest {

    /** Cache stub: hand the Resolver exactly the evidence we want. */
    static class StubCache extends ObservationCache {
        final List<Observation<?>> data = new ArrayList<>();
        @Override public Collection<Observation<?>> all() { return data; }
    }

    private final StubCache cache = new StubCache();

    private Resolver resolver() {
        Resolver r = new Resolver();
        r.cache = cache;
        r.cfg = Fakes.failoverConfig(List.of("test.client"), false);
        r.aggregators = Fakes.instanceOf(List.of(TestPlugin.aggregator()));
        return r;
    }

    private void seat(Vantage v, HealthState state) {
        cache.data.add(TestPlugin.obs("c1", "node-x", "pub-" + v, "test." + v, v, state));
    }

    @Test void allSeatsHealthy_isUp() {
        seat(Vantage.CLIENT, FAST);
        seat(Vantage.LOCAL_SELF, FAST);
        assertEquals(Verdict.UP, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void midBandSeat_isDegraded() {
        seat(Vantage.CLIENT, DEGRADED);          // 0.5 — between 0.25 and 0.75
        seat(Vantage.LOCAL_SELF, FAST);
        assertEquals(Verdict.DEGRADED, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void singleReportingSeatDown_isSdownNotOdown() {
        seat(Vantage.CLIENT, DEAD);
        assertEquals(Verdict.SDOWN, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void minoritySeatDown_isSdown() {
        seat(Vantage.CLIENT, DEAD);
        seat(Vantage.LOCAL_SELF, FAST);
        seat(Vantage.REMOTE_PEER, FAST);
        assertEquals(Verdict.SDOWN, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void oneOfTwoSeatsDown_isNotAMajority() {
        seat(Vantage.CLIENT, DEAD);
        seat(Vantage.LOCAL_SELF, FAST);
        assertEquals(Verdict.SDOWN, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void majoritySeatsDown_isOdown() {
        seat(Vantage.CLIENT, DEAD);
        seat(Vantage.REMOTE_PEER, DEAD);
        seat(Vantage.LOCAL_SELF, FAST);
        assertEquals(Verdict.ODOWN, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void allSeatsDown_isOdown() {
        seat(Vantage.CLIENT, DEAD);
        seat(Vantage.REMOTE_PEER, DEAD);
        assertEquals(Verdict.ODOWN, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void worstPluginOpinionWinsTheSeat() {
        // Same seat, same plugin: aggregator averages within the bucket
        // (FAST + DEAD = 0.5 → degraded band, not down).
        seat(Vantage.CLIENT, FAST);
        seat(Vantage.CLIENT, DEAD);
        assertEquals(Verdict.DEGRADED, resolver().verdictFor("node-x").orElseThrow());
    }

    @Test void noEvidence_isEmpty() {
        assertTrue(resolver().verdictFor("node-x").isEmpty());
    }

    @Test void deterministic_twoInstancesAgree() {
        seat(Vantage.CLIENT, DEAD);
        seat(Vantage.LOCAL_SELF, DEGRADED);
        seat(Vantage.REMOTE_PEER, DEAD);
        assertEquals(resolver().all().get("node-x").verdict(),
                     resolver().all().get("node-x").verdict());
    }

    @Test void unknownIsSilence_notEvidence() {
        seat(Vantage.CLIENT, HealthState.UNKNOWN);
        assertTrue(resolver().verdictFor("node-x").isEmpty());
    }
}
