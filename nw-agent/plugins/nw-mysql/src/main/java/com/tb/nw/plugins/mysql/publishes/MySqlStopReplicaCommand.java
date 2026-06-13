package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Directive to a slave: STOP REPLICA (and optionally RESET REPLICA ALL).
 *
 * <p>Used during fence-and-promote (the chosen slave stops replicating before
 * being promoted) and during graceful drain (a slave stops cleanly before
 * being decommissioned).</p>
 */
public record MySqlStopReplicaCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode,
        /* true = also RESET REPLICA ALL (drops the replication channel state) */
        boolean resetAll
) implements MySqlCommandEvent {

    @Override public String commandKind() { return "stop-replica"; }

    public static MySqlStopReplicaCommand of(long epoch, String targetNode, boolean resetAll) {
        return new MySqlStopReplicaCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode,
                resetAll);
    }
}
