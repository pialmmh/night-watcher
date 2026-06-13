package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Directive to a (potentially still-reachable) master: SET read_only=1,
 * KILL active write connections, drop the cluster VIP if it holds one.
 *
 * <p>Sent before the promotion command so the cluster has zero windows of
 * dual-mastership. Idempotent — re-applying on an already-fenced node
 * short-circuits via the action's idempotency check.</p>
 */
public record MySqlFenceMasterCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode,
        /* true = drop the VIP this node currently owns (if any) */
        boolean dropVip
) implements MySqlCommandEvent {

    @Override public String commandKind() { return "fence-master"; }

    public static MySqlFenceMasterCommand of(long epoch, String targetNode, boolean dropVip) {
        return new MySqlFenceMasterCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode,
                dropVip);
    }
}
