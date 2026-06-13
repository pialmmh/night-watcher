package com.tb.nw.plugins.mock.api;

import com.tb.nw.plugins.mock.api.MockPluginDescriptor;
import com.tb.nw.plugins.mock.publishes.MockHealth;
import com.tb.nw.spi.api.ClusterType;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.Vantage;
import com.tb.nw.spi.api.VantageBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockHealthAggregatorTest {

    private final MockHealthAggregator agg = build();

    private static MockHealthAggregator build() {
        MockHealthAggregator a = new MockHealthAggregator();
        a.descriptor = new MockPluginDescriptor();
        return a;
    }

    private static Observation<MockHealth> obs(String publisher, HealthState state, Instant when) {
        MockHealth h = new MockHealth(
                com.tb.nw.spi.api.NwEvent.newEventId(), when,
                MockPluginDescriptor.PLUGIN_ID, MockPluginDescriptor.PLUGIN_VERSION,
                state, "node-x", state.name().toLowerCase(), "slave", false, "test");
        return new Observation<>("c1", ClusterType.GENERIC, "node-x", publisher, "mock.client",
                Vantage.CLIENT, state, 1L, when, h.pluginId(), h.pluginVersion(), h);
    }

    private static VantageBucket<MockHealth> bucket(Observation<MockHealth>... obs) {
        return new VantageBucket<>("node-x", Vantage.CLIENT, List.of(obs));
    }

    @Test void fastIsOne_deadIsZero_degradedIsHalf() {
        Instant now = Instant.now();
        assertEquals(1.0, agg.scoreVantage(bucket(obs("p1", HealthState.FAST, now))));
        assertEquals(0.0, agg.scoreVantage(bucket(obs("p1", HealthState.DEAD, now))));
        assertEquals(0.5, agg.scoreVantage(bucket(obs("p1", HealthState.DEGRADED, now))));
    }

    @Test void freshestPerPublisherWins() {
        Instant old = Instant.now().minusSeconds(20);
        Instant now = Instant.now();
        // same publisher: stale FAST is superseded by fresh DEAD
        assertEquals(0.0, agg.scoreVantage(bucket(
                obs("p1", HealthState.FAST, old), obs("p1", HealthState.DEAD, now))));
    }

    @Test void publishersAverage() {
        Instant now = Instant.now();
        assertEquals(0.5, agg.scoreVantage(bucket(
                obs("p1", HealthState.FAST, now), obs("p2", HealthState.DEAD, now))));
    }

    @Test void unknownIsNoSignal() {
        Instant now = Instant.now();
        assertTrue(Double.isNaN(agg.scoreVantage(bucket(obs("p1", HealthState.UNKNOWN, now)))));
    }

    @Test void emptyBucketIsNaN() {
        assertTrue(Double.isNaN(agg.scoreVantage(bucket())));
    }
}
