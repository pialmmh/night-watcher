package com.tb.nw.plugins.mock.probe;

import com.tb.nw.plugins.mock.MockConfig;
import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.plugins.mock.events.MockHealth;
import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.ProbeContext;
import com.tb.nw.spi.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Set;

/**
 * REMOTE_PEER seat — a cluster member pinging the watched master from the
 * storage tier. Activation predicate: a watch target is known AND it is not
 * me. The {@code @peer} override file simulates the storage-tier partition
 * the other seats can't see.
 */
@ApplicationScoped
public class MockPeerProbe implements HealthCheck<MockHealth> {

    @Inject MockPluginDescriptor descriptor;
    @Inject MockConfig cfg;
    @Inject MockStateStore store;

    @Override public String id() { return "mock.peer"; }
    @Override public Vantage vantage() { return Vantage.REMOTE_PEER; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MockHealth> eventType() { return MockHealth.class; }
    @Override public Set<String> requiredFacets() { return Set.of("mock-service-here"); }

    /** "A master is known AND it is not me" — else skip the tick. */
    @Override public String target(ProbeContext ctx) {
        return cfg.watchTarget()
                .filter(t -> !t.equals(ctx.localNode()))
                .orElse(null);
    }

    @Override
    public HealthReport<MockHealth> probe(ProbeContext ctx) {
        long t0 = System.nanoTime();
        String target = cfg.watchTarget().orElseThrow();
        String raw = store.stateSeenBy(target, "peer");
        MockHealth event = MockHealth.of(
                MockStateStore.grade(raw), target,
                raw, store.roleOf(target).orElse(null), MockStateStore.FENCED.equals(raw),
                "peer-ping");
        return HealthReport.of(event, Duration.ofNanos(System.nanoTime() - t0));
    }
}
