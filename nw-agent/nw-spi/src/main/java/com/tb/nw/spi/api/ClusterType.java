package com.tb.nw.spi.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * Typed classifier for a cluster's shape. Drives plugin selection: each
 * plugin declares which ClusterType(s) it handles in its manifest.
 *
 * The enum holds the canonical built-in set. Custom plugins can extend by
 * registering additional type IDs through {@link #parse(String)} returning
 * empty — in which case the agent treats it as an unknown type and rejects
 * the cluster at startup, prompting the operator to install the right plugin.
 *
 * displayName() is the form used in config, observations, and audit — chosen
 * to match how operators refer to these systems in conversation.
 */
public enum ClusterType {

    // MySQL flavors
    MYSQL_MASTER_SLAVE("MySqlMasterSlave"),
    MYSQL_GALERA("MySqlGalera"),
    MYSQL_INNODB_CLUSTER("MySqlInnoDbCluster"),

    // PostgreSQL flavors
    POSTGRES_STREAMING("PostgresStreaming"),
    POSTGRES_PATRONI("PostgresPatroni"),

    // Network / web
    NGINX("Nginx"),
    HAPROXY("HAProxy"),

    // Telecom
    SMS_GATEWAY("SmsGateway"),
    SIGTRAN_SGW("SigtranSgw"),
    FREESWITCH("FreeSwitch"),

    // Messaging
    KAFKA("Kafka"),
    REDIS_SENTINEL("RedisSentinel"),

    // Generic / fallback
    GENERIC("Generic");

    private final String displayName;

    ClusterType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static ClusterType fromJson(String s) {
        return parse(s).orElse(GENERIC);
    }

    public static Optional<ClusterType> parse(String s) {
        if (s == null) return Optional.empty();
        for (ClusterType t : values()) {
            if (t.displayName.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
