package com.tb.nw.core.lifecycle;

import com.tb.nw.core.AgentConfig;
import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.core.coordinator.FailoverCoordinator;
import com.tb.nw.core.observe.FacetPublisher;
import com.tb.nw.core.observe.FacetRegistry;
import com.tb.nw.core.observe.ObservationPublisher;
import com.tb.nw.core.vote.Resolver;

import com.tb.nw.fabric.internal.EtcdFabric;
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
    @Inject ObservationCache cache;
    @Inject com.tb.nw.core.lifecycle.AgentLifecycleMachine lifecycle;
    @Inject com.tb.nw.core.coordinator.FailoverCoordinator coordinator;
    @Inject com.tb.nw.core.vote.Resolver resolver;
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
        out.put("fabric_endpoints", cfg.fabricEndpoints());
        out.put("fabric_reachable", fabricReachable());
        out.put("last_heartbeat_ok", asString(state.lastHeartbeatOk()));
        out.put("last_error", state.lastError());
        out.put("facets", facets.snapshot());
        out.put("facet_detectors", registry.detectorClassNames());
        out.put("observations_published", observations.snapshot());
        out.put("observations_in_cache", cache.size());
        out.put("lifecycle_phase", lifecycle.currentPhase());
        out.put("failover_phase", coordinator.currentPhase());
        out.put("verdicts", verdicts());
        var lastTrigger = coordinator.lastTrigger();
        if (lastTrigger != null) {
            out.put("last_failover_trigger", Map.of(
                    "target", lastTrigger.target(),
                    "investigator", lastTrigger.investigator(),
                    "consecutive_failures", lastTrigger.consecutiveFailures()));
        }
        return out;
    }

    private boolean fabricReachable() {
        return fabric instanceof EtcdFabric e && e.ping();
    }

    /** Per-target Resolver verdicts — seat scores included so a curl shows the vote. */
    private Map<String, Object> verdicts() {
        Map<String, Object> out = new LinkedHashMap<>();
        resolver.all().forEach((target, tv) -> out.put(target, Map.of(
                "verdict", tv.verdict().name(),
                "seats_reporting", tv.seatsReporting(),
                "seats_down", tv.seatsDown(),
                "seat_scores", tv.seatScores().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                e -> e.getKey().name(), Map.Entry::getValue)))));
        return out;
    }

    private static String asString(Instant t) {
        return t == null ? null : t.toString();
    }
}
