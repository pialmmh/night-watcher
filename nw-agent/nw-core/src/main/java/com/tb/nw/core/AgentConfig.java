package com.tb.nw.core;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

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
     * Typed classifier — see {@link com.tb.nw.spi.ClusterType}. Drives plugin
     * selection. Default is GENERIC for the early-bring-up case.
     */
    @WithDefault("Generic")
    String clusterType();

    /** Local etcd client URL. */
    @WithDefault("http://127.0.0.1:2379")
    String fabricEndpoint();

    /** Heartbeat publishing interval (seconds). */
    @WithDefault("5")
    int heartbeatIntervalSec();

    /** Heartbeat lease TTL (seconds). Typically 3× interval. */
    @WithDefault("15")
    int heartbeatTtlSec();
}
