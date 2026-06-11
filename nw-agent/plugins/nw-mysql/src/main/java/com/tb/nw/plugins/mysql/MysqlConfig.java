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

    /**
     * Password from env var <em>NW_MYSQL_PASSWORD</em>. Optional — when absent
     * the probe attempts a passwordless connection (which mysqld will reject
     * unless explicitly configured for it). The wrapping value is Optional
     * so the agent boots cleanly on nodes that don't run any MySQL probe.
     */
    Optional<String> password();

    /**
     * Remote MySQL master host the REMOTE_PEER probe should target.
     * Activation engine will replace this with a topology-derived address;
     * for now it's configured statically.
     */
    Optional<String> remoteHost();

    /**
     * Remote MySQL slave host the REMOTE_PEER slave probe should target.
     * Multi-slave coverage lands when target_selector is implemented;
     * single-target is enough for the first cut.
     */
    Optional<String> remoteSlaveHost();

    /**
     * The host a JDBC client would connect to (typically the master VIP).
     * Used by the CLIENT-vantage probe. Defaults to {@code remoteHost} when
     * unset, since most deployments share the address.
     */
    Optional<String> clientTargetHost();

    /** Heartbeat table queried by canary probes. */
    @WithDefault("nw_heartbeat")
    String heartbeatTable();

    /**
     * Query the client-vantage probe runs each tick. Default is
     * {@code SHOW DATABASES} — cheap, requires no special privileges, returns
     * rows on any healthy mysqld. Operators with a richer canary in mind
     * (e.g. {@code SELECT 1 FROM nw_heartbeat WHERE k='ping'}) override here.
     *
     * The probe classifies as {@code DEAD} if the query throws and as
     * {@code DEGRADED} if the query returns zero rows.
     */
    @WithDefault("SHOW DATABASES")
    String customQuery();

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

    /**
     * Replication user the start-replica action wires into CHANGE
     * REPLICATION SOURCE / CHANGE MASTER. From env
     * {@code NW_MYSQL_REPLICATION_USER} — commands carry only a credentials
     * REFERENCE; the secret resolves here, on the target agent.
     */
    Optional<String> replicationUser();

    /** Replication password — env {@code NW_MYSQL_REPLICATION_PASSWORD}. Never in YAML. */
    Optional<String> replicationPassword();
}
