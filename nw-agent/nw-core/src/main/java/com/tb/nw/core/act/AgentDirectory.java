package com.tb.nw.core.act;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tb.nw.core.AgentConfig;
import com.tb.nw.core.AgentLease;
import com.tb.nw.core.FacetPublisher;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * The Registry board — the agents' phone book at
 * {@code /clusters/{c}/agents/{node}}, lease-bound: an entry appears when
 * its agent boots and vanishes by itself when the agent dies.
 *
 * <p>Publishes THIS agent's entry on a schedule (refreshes the lease
 * binding) and looks up peers for the Dispatcher.</p>
 */
@Startup
@ApplicationScoped
public class AgentDirectory {

    private static final Logger LOG = Logger.getLogger(AgentDirectory.class);

    /** One directory entry. {@code commandUrl} is the agent's command door. */
    public record AgentEntry(String node, String commandUrl, Set<String> facets) {}

    @Inject AgentConfig cfg;
    @Inject AgentLease lease;
    @Inject Fabric fabric;
    @Inject FacetPublisher facets;
    @Inject ObjectMapper json;

    @ConfigProperty(name = "quarkus.http.host", defaultValue = "127.0.0.1")
    String bindHost;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "7102")
    int httpPort;

    @Scheduled(every = "15s", delayed = "4s")
    void publishSelf() {
        try {
            AgentEntry entry = new AgentEntry(cfg.nodeName(), commandUrl(), facets.snapshot());
            fabric.kv().put(key(cfg.nodeName()), json.writeValueAsBytes(entry), lease.current());
        } catch (FabricException e) {
            LOG.debugf("agent directory publish skipped (fabric): %s", e.getMessage());
        } catch (Exception e) {
            LOG.warnf(e, "agent directory publish failed");
        }
    }

    /** Look up a peer's entry. Empty when the agent is gone (lease expired) or never registered. */
    public Optional<AgentEntry> lookup(String node) {
        try {
            return fabric.kv().get(key(node)).map(this::parse);
        } catch (FabricException e) {
            LOG.warnf("agent directory lookup failed for %s: %s", node, e.getMessage());
            return Optional.empty();
        }
    }

    private AgentEntry parse(byte[] body) {
        try {
            return json.readValue(body, AgentEntry.class);
        } catch (Exception e) {
            throw new IllegalStateException("malformed agent directory entry", e);
        }
    }

    /**
     * This agent's advertised command door. Host precedence: explicit
     * {@code nw.agent.advertise-host} → the HTTP bind host (the overlay IP
     * in prod/staging via NW_BIND_HOST; 127.0.0.1 in dev).
     */
    private String commandUrl() {
        String host = cfg.advertiseHost().orElse(bindHost);
        return "http://" + host + ":" + httpPort + "/agent/command";
    }

    private String key(String node) {
        return "/clusters/" + cfg.clusterName() + "/agents/" + node;
    }
}
