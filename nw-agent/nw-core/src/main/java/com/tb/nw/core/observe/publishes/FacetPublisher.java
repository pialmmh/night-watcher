package com.tb.nw.core.observe.publishes;
import com.tb.nw.core.observe.api.FacetRegistry;

import com.tb.nw.core.dependencies.AgentConfig;
import com.tb.nw.core.lifecycle.internal.AgentLease;
import com.tb.nw.core.lifecycle.internal.AgentState;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects local facets and publishes them to /agents/{node}/facets under the
 * shared agent lease. Re-runs detection periodically so transitions
 * (service started, service stopped) become visible cluster-wide.
 */
@Startup
@ApplicationScoped
public class FacetPublisher {

    private static final Logger LOG = Logger.getLogger(FacetPublisher.class);

    @Inject AgentConfig cfg;
    @Inject AgentState state;
    @Inject AgentLease lease;
    @Inject Fabric fabric;
    @Inject FacetRegistry registry;

    private final AtomicReference<Set<String>> lastPublished = new AtomicReference<>(Set.of());

    @Scheduled(every = "30s", delayed = "3s")
    public void tick() {
        try {
            Set<String> current = registry.detectAll();
            if (!current.equals(lastPublished.get())) {
                writeFacets(current);
                LOG.infof("facets changed → %s", current);
                lastPublished.set(current);
            } else {
                writeFacets(current);                    // refresh lease binding
            }
        } catch (FabricException e) {
            LOG.warnf("facet publish failed: %s", e.getMessage());
            state.markError("facets: " + e.getMessage());
        }
    }

    private void writeFacets(Set<String> facets) {
        String key = "/agents/" + cfg.nodeName() + "/facets";
        String csv = String.join(",", facets);
        fabric.kv().put(key, csv.getBytes(StandardCharsets.UTF_8), lease.current());
    }

    public Set<String> snapshot() {
        return lastPublished.get();
    }
}
