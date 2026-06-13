package com.tb.nw.plugins.mock.api;
import com.tb.nw.plugins.mock.internal.MockStateStore;

import com.tb.nw.plugins.mock.dependencies.MockConfig;
import com.tb.nw.plugins.mock.api.MockPluginDescriptor;
import com.tb.nw.spi.api.FacetDetector;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Detects the mock service from the state dir and publishes the facets that
 * arm the probes:
 *
 *   mock-service-here   — this node has a {node}.role file (a mock service "runs" here)
 *   mock-master-here    — role file says master
 *   mock-slave-here     — role file says slave
 *   mock-client-here    — nw.mock.watch-target is configured (this host plays the app tier)
 *
 * No state dir → no facets → every mock probe stays dormant. That is the
 * safety latch for real deployments that happen to ship this plugin.
 */
@ApplicationScoped
public class MockFacetDetector implements FacetDetector {

    private static final Set<String> DECLARED = Set.of(
            "mock-service-here", "mock-master-here", "mock-slave-here", "mock-client-here");

    @Inject MockConfig cfg;
    @Inject MockStateStore store;
    @Inject MockPluginDescriptor descriptor;

    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Set<String> declaredFacets() { return DECLARED; }

    @Override public Set<String> detect() {
        if (!store.stateDirExists()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        store.roleOf(store.localNode()).ifPresent(role -> {
            out.add("mock-service-here");
            if ("master".equals(role)) out.add("mock-master-here");
            if ("slave".equals(role)) out.add("mock-slave-here");
        });
        if (cfg.watchTarget().isPresent()) out.add("mock-client-here");
        return out;
    }
}
