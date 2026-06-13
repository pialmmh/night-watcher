package com.tb.nw.core.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tb.nw.core.PluginRegistry;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.Vantage;
import com.tb.nw.testkit.FakeFabric;
import com.tb.nw.testkit.Fakes;
import com.tb.nw.testkit.TestPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.tb.nw.spi.api.HealthState.DEAD;
import static com.tb.nw.spi.api.HealthState.FAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationCacheTest {

    static final String KEY = "/clusters/c1/observations/node-x/pub1/test.client";

    FakeFabric fabric;
    ObjectMapper json;
    ObservationCache cache;
    List<ObservationCache.ChangeEvent> events;

    @BeforeEach void setUp() {
        fabric = new FakeFabric();
        json = new ObjectMapper().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        PluginRegistry registry = com.tb.nw.core.TestCore.pluginRegistry(
                List.of(TestPlugin.descriptor()));

        cache = new ObservationCache();
        cache.cfg = Fakes.agentConfig("node-a", "c1");
        cache.fabric = fabric;
        cache.json = json;
        cache.plugins = registry;
        cache.listenerExecutor.shutdown();
        cache.listenerExecutor = Fakes.directExecutor();   // synchronous for assertions
        events = new ArrayList<>();
        cache.addListener(events::add);
        cache.start();
    }

    private byte[] envelope(com.tb.nw.spi.api.HealthState state) throws Exception {
        Observation<?> o = TestPlugin.obs("c1", "node-x", "pub1", "test.client",
                Vantage.CLIENT, state);
        return json.writeValueAsBytes(o);
    }

    @Test void validObservation_isCachedTypedAndAnnounced() throws Exception {
        fabric.kv().put(KEY, envelope(FAST));
        assertEquals(1, cache.size());
        assertEquals(1, events.size());
        assertEquals(ObservationCache.ChangeEvent.Kind.ADDED, events.get(0).kind());
        assertInstanceOf(TestPlugin.TestHealth.class, cache.all().iterator().next().event());
    }

    @Test void updateOfSameKey_isUpdated() throws Exception {
        fabric.kv().put(KEY, envelope(FAST));
        fabric.kv().put(KEY, envelope(DEAD));
        assertEquals(1, cache.size());
        assertEquals(ObservationCache.ChangeEvent.Kind.UPDATED, events.get(1).kind());
    }

    @Test void versionMismatch_isDroppedBeforeTypedParse() throws Exception {
        String body = new String(envelope(FAST), StandardCharsets.UTF_8)
                .replace("\"9.9.9\"", "\"1.0.0\"");
        fabric.kv().put(KEY, body.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, cache.size());
        assertTrue(events.isEmpty());
    }

    @Test void unknownPlugin_isDropped() throws Exception {
        String body = new String(envelope(FAST), StandardCharsets.UTF_8)
                .replace("\"nw-test\"", "\"nw-stranger\"");
        fabric.kv().put(KEY, body.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, cache.size());
    }

    @Test void garbage_isDroppedQuietly() {
        fabric.kv().put(KEY, "not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, cache.size());
    }

    @Test void nonObservationKey_isIgnored() throws Exception {
        fabric.kv().put("/clusters/c1/roles/node-x", envelope(FAST));
        assertEquals(0, cache.size());
    }

    @Test void leaseExpiry_removesAndAnnounces() throws Exception {
        var lease = fabric.leases().grant(java.time.Duration.ofSeconds(15));
        fabric.kv().put(KEY, envelope(FAST), lease);
        fabric.expireLease(lease.id());
        assertEquals(0, cache.size());
        assertEquals(ObservationCache.ChangeEvent.Kind.REMOVED, events.get(1).kind());
    }

    @Test void bootSeed_readsExistingBoard() throws Exception {
        fabric.kv().put(KEY, envelope(FAST));
        ObservationCache second = new ObservationCache();
        second.cfg = cache.cfg;
        second.fabric = fabric;
        second.json = json;
        second.plugins = cache.plugins;
        second.start();
        assertEquals(1, second.size());
    }
}
