package com.tb.nw.plugins.mysql;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * MySQL plugin configuration. Values come from agent application.yml or env.
 * The plugin reads its own slice — keeps the core agent decoupled from
 * service-specific knobs.
 */
@ConfigMapping(prefix = "nw.mysql")
public interface MysqlConfig {

    @WithDefault("127.0.0.1")
    String localHost();

    @WithDefault("3306")
    int port();

    @WithDefault("root")
    String username();

    /** Password from env var <em>NW_MYSQL_PASSWORD</em> in real deployments. */
    @WithDefault("")
    String password();

    /** Optional remote host to probe for remote-peer / client-vantage checks. */
    Optional<String> remoteHost();

    /** Heartbeat table queried by canary probes. */
    @WithDefault("nw_heartbeat")
    String heartbeatTable();

    @WithDefault("50")
    long fastThresholdMs();

    @WithDefault("500")
    long degradedThresholdMs();

    @WithDefault("5000")
    long deadThresholdMs();

    @WithDefault("2")
    int connectTimeoutSec();

    @WithDefault("3")
    int queryTimeoutSec();
}
