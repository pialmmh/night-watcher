package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Directive to flip {@code @@global.read_only} (and {@code super_read_only}).
 * Used both during fence (read_only=true) and during promotion (read_only=false).
 */
public record MySqlSetReadOnlyCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode,
        boolean readOnly
) implements MySqlCommandEvent {

    @Override public String commandKind() { return "set-read-only"; }

    public static MySqlSetReadOnlyCommand of(long epoch, String targetNode, boolean readOnly) {
        return new MySqlSetReadOnlyCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode,
                readOnly);
    }
}
