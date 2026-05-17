package com.tb.nw.core;

import com.tb.nw.fabric.etcd.EtcdFabric;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live agent status. Returns enough for a curl-based smoke test to decide
 * whether the agent has come up, is talking to the fabric, and has detected
 * its local role.
 */
@ApplicationScoped
@Path("/agent")
public class AgentInfo {

    @Inject AgentConfig cfg;
    @Inject AgentState state;
    @Inject FacetPublisher facets;
    @Inject FacetRegistry registry;
    @Inject ObservationPublisher observations;
    @Inject com.tb.nw.fabric.api.Fabric fabric;

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> info() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "night-watcher");
        out.put("version", "1.0.0-SNAPSHOT");
        out.put("node", cfg.nodeName());
        out.put("cluster", cfg.clusterName());
        out.put("cluster_type", cfg.clusterType());
        out.put("status", state.status().name());
        out.put("fabric_endpoint", cfg.fabricEndpoint());
        out.put("fabric_reachable", fabricReachable());
        out.put("last_heartbeat_ok", asString(state.lastHeartbeatOk()));
        out.put("last_error", state.lastError());
        out.put("facets", facets.snapshot());
        out.put("facet_detectors", registry.detectorClassNames());
        out.put("observations_published", observations.snapshot());
        return out;
    }

    private boolean fabricReachable() {
        return fabric instanceof EtcdFabric e && e.ping();
    }

    private static String asString(Instant t) {
        return t == null ? null : t.toString();
    }
}
