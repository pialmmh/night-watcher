package com.tb.nw.plugins.mysql.events;

import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.spi.NwEvent;

import java.time.Instant;
import java.util.Map;

/**
 * Generic result event a {@code nw-mysql} action returns to the Dispatcher.
 *
 * <p>Carries the original command's id ({@link #inReplyToEventId}) for
 * correlation, a coarse {@link #ok} flag, the action's {@code commandKind}
 * (so a single result class serves all commands), and an open-ended
 * {@link #detail} for action-specific outputs (gtid position after promote,
 * number of killed connections during fence, etc.).</p>
 *
 * <p>If a specific command grows enough payload to warrant its own result
 * type, define {@code MySqlPromoteResult implements MySqlCommandResultEvent}
 * with typed fields; both will coexist under the
 * {@link MySqlCommandResultEvent} marker.</p>
 */
public record MySqlCommandResult(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        String inReplyToEventId,
        boolean ok,
        String reason,
        String commandKind,
        Map<String, String> detail
) implements MySqlCommandResultEvent {

    public static MySqlCommandResult ok(String inReplyToEventId, String commandKind, Map<String, String> detail) {
        return new MySqlCommandResult(
                NwEvent.newEventId(), Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID, MysqlPluginDescriptor.PLUGIN_VERSION,
                inReplyToEventId, true, "ok",
                commandKind, detail == null ? Map.of() : Map.copyOf(detail));
    }

    public static MySqlCommandResult failure(String inReplyToEventId, String commandKind, String reason) {
        return new MySqlCommandResult(
                NwEvent.newEventId(), Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID, MysqlPluginDescriptor.PLUGIN_VERSION,
                inReplyToEventId, false, reason,
                commandKind, Map.of());
    }
}
