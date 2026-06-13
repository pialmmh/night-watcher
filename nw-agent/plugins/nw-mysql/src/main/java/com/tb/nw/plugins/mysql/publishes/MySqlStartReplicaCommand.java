package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Directive to a slave: configure replication source + START REPLICA.
 *
 * <p>Issued during a fresh slave attach (bootstrap or after promotion when
 * the previous master returns and must rejoin as a replica of the new master).</p>
 */
public record MySqlStartReplicaCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode,
        /* hostname / IP of the new master */
        String sourceHost,
        int sourcePort,
        /* replication credentials reference (vault path or k8s secret name; never the secret value itself) */
        String replicationCredentialsRef
) implements MySqlCommandEvent {

    @Override public String commandKind() { return "start-replica"; }

    public static MySqlStartReplicaCommand of(long epoch, String targetNode,
                                              String sourceHost, int sourcePort,
                                              String replicationCredentialsRef) {
        return new MySqlStartReplicaCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode,
                sourceHost, sourcePort,
                replicationCredentialsRef);
    }
}
