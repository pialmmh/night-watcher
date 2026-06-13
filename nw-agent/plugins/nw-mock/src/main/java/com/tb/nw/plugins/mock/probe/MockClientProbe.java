package com.tb.nw.plugins.mock.probe;

import com.tb.nw.plugins.mock.MockConfig;
import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.plugins.mock.events.MockHealth;
import com.tb.nw.spi.api.HealthCheck;
import com.tb.nw.spi.api.HealthReport;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.ProbeContext;
import com.tb.nw.spi.api.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Set;

/**
 * CLIENT seat — an application impersonator "calling" the watched master.
 * The call is a read of the target's state file; the {@code @client}
 * override file simulates what only the app tier can see (a partition, a
 * firewall rule) without touching the truth.
 */
@ApplicationScoped
public class MockClientProbe implements HealthCheck<MockHealth> {

    @Inject MockPluginDescriptor descriptor;
    @Inject MockConfig cfg;
    @Inject MockStateStore store;

    @Override public String id() { return "mock.client"; }
    @Override public Vantage vantage() { return Vantage.CLIENT; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MockHealth> eventType() { return MockHealth.class; }
    @Override public Set<String> requiredFacets() { return Set.of("mock-client-here"); }

    /** Watch target from config; null (unset) skips the tick. */
    @Override public String target(ProbeContext ctx) {
        return cfg.watchTarget().orElse(null);
    }

    @Override
    public HealthReport<MockHealth> probe(ProbeContext ctx) {
        long t0 = System.nanoTime();
        String target = cfg.watchTarget().orElseThrow();
        String raw = store.stateSeenBy(target, "client");
        boolean fenced = MockStateStore.FENCED.equals(raw);
        MockHealth event = MockHealth.of(
                MockStateStore.grade(raw), target,
                raw, store.roleOf(target).orElse(null), fenced,
                fenced ? "target refuses connections (fenced)" : "client-call");
        return HealthReport.of(event, Duration.ofNanos(System.nanoTime() - t0));
    }
}
