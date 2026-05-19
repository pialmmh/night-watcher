package com.tb.nw.core;

import com.tb.nw.spi.PluginEntity;

/**
 * Typed body of every {@link HostUptimeHealthCheck} observation.
 */
public record HostUptimeDetail(
        String pluginId,
        String pluginVersion,
        long uptimeSeconds,
        String uptimeHuman,
        String reason            // non-null when the probe couldn't read /proc/uptime
) implements PluginEntity {

    public static HostUptimeDetail ok(long uptimeSeconds, String uptimeHuman) {
        return new HostUptimeDetail(
                CorePluginDescriptor.PLUGIN_ID,
                CorePluginDescriptor.PLUGIN_VERSION,
                uptimeSeconds, uptimeHuman, null);
    }

    public static HostUptimeDetail unreadable(String reason) {
        return new HostUptimeDetail(
                CorePluginDescriptor.PLUGIN_ID,
                CorePluginDescriptor.PLUGIN_VERSION,
                0L, null, reason);
    }
}
