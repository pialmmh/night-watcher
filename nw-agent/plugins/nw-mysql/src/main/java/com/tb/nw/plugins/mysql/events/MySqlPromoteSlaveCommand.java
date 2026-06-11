package com.tb.nw.plugins.mysql.events;

import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.spi.NwEvent;

import java.time.Instant;

/**
 * Directive from the orchestrator to a target slave: STOP REPLICA, RESET
 * REPLICA ALL, SET read_only=0, accept writes. Issued by the
 * FailoverPlanGenerator after the CandidateSelector has chosen this node
 * as the promotion target.
 */
public record MySqlPromoteSlaveCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode,
        /* GTID position the new master must have replayed at minimum; null = accept any */
        String requiredGtidOrNull
) implements MySqlCommandEvent {

    @Override public String commandKind() { return "promote-slave"; }

    public static MySqlPromoteSlaveCommand of(long epoch, String targetNode, String requiredGtidOrNull) {
        return new MySqlPromoteSlaveCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode,
                requiredGtidOrNull);
    }
}
