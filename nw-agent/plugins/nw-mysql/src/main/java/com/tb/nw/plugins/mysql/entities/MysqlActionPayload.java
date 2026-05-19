package com.tb.nw.plugins.mysql.entities;

import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.spi.PluginEntity;

import java.util.Map;

/**
 * Typed payload consumed by every nw-mysql {@code FailoverAction}.
 *
 * <p>Stub — the Dispatcher + concrete actions (mysql.fence-self,
 * mysql.promote-self, mysql.start-replica, mysql.stop-replica) are not yet
 * wired. The shape is defined now so the type parameters of
 * {@code FailoverAction<MysqlActionPayload>} and
 * {@code Plan<MysqlActionPayload>} are stable.</p>
 */
public record MysqlActionPayload(
        String pluginId,
        String pluginVersion,
        String operation,             // "fence-self", "promote-self", "start-replica", "stop-replica", ...
        String targetNode,            // node id the action is acting on
        Map<String, String> args      // typed-but-flexible per-operation args (kept narrow on purpose)
) implements PluginEntity {

    public static MysqlActionPayload of(String operation, String targetNode, Map<String, String> args) {
        return new MysqlActionPayload(
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                operation, targetNode,
                args == null ? Map.of() : Map.copyOf(args));
    }
}
