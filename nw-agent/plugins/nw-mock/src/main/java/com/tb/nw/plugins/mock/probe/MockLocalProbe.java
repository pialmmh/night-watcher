package com.tb.nw.plugins.mock.probe;

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
 * LOCAL_SELF seat — the mock service confessing its own state. This is the
 * evidence stream the selector promotes from (it carries the role).
 */
@ApplicationScoped
public class MockLocalProbe implements HealthCheck<MockHealth> {

    @Inject MockPluginDescriptor descriptor;
    @Inject MockStateStore store;

    @Override public String id() { return "mock.local"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MockHealth> eventType() { return MockHealth.class; }
    @Override public Set<String> requiredFacets() { return Set.of("mock-service-here"); }

    @Override
    public HealthReport<MockHealth> probe(ProbeContext ctx) {
        long t0 = System.nanoTime();
        String node = ctx.localNode();
        String raw = store.stateOf(node);
        String role = store.roleOf(node).orElse(null);
        MockHealth event = MockHealth.of(
                MockStateStore.grade(raw), node,
                raw, role, MockStateStore.FENCED.equals(raw),
                "self-read");
        return HealthReport.of(event, Duration.ofNanos(System.nanoTime() - t0));
    }
}
