package com.tb.nw.core;

import com.tb.nw.core.lifecycle.Heartbeat;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Agent-level config. Backed by Quarkus / SmallRye Config; values come from
 * application.yml and / or environment variables.
 */
@ConfigMapping(prefix = "nw.agent")
public interface AgentConfig {

    /** This node's name. Must match the etcd node identity. */
    @WithDefault("unknown")
    String nodeName();

    /** Default cluster this agent participates in. Multi-cluster comes later. */
    @WithDefault("nw-default")
    String clusterName();

    /**
     * Typed classifier — see {@link com.tb.nw.spi.api.ClusterType}. Drives plugin
     * selection. Default is GENERIC for the early-bring-up case.
     */
    @WithDefault("Generic")
    String clusterType();

    /**
     * etcd client URL(s). Single entry for dev / smoke; in production a 3- or
     * 5-node cluster supplies the full peer list so the jetcd client can fall
     * back if the local peer is down.
     *
     * Comma-separated in env var form, e.g.
     * {@code NW_AGENT_FABRIC_ENDPOINTS=http://10.0.0.1:2379,http://10.0.0.2:2379,http://10.0.0.3:2379}.
     */
    @WithDefault("http://127.0.0.1:2379")
    List<String> fabricEndpoints();

    /** Heartbeat publishing interval (seconds). */
    @WithDefault("5")
    int heartbeatIntervalSec();

    /** Heartbeat lease TTL (seconds). Typically 3× interval. */
    @WithDefault("15")
    int heartbeatTtlSec();

    /** Per-probe deadline (seconds). Must stay inside the 5 s investigator tick. */
    @WithDefault("4")
    int probeDeadlineSec();

    /**
     * Host peers should use to reach this agent's command door. Defaults to
     * the HTTP bind host when unset — override (env
     * {@code NW_AGENT_ADVERTISE_HOST}) when binding and advertising differ.
     */
    Optional<String> advertiseHost();
}
