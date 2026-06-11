package com.tb.nw.core;

import com.tb.nw.spi.HealthCheckEvent;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.NwEvent;

import java.time.Instant;

/**
 * Built-in {@link HealthCheckEvent} the {@link HostUptimeHealthCheck} emits.
 * Carried under the {@code nw-core} plugin id so the framework treats it
 * exactly the same way it treats real plugin events (typed + version-stamped).
 */
public record HostUptimeEvent(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        HealthState healthState,
        String target,
        long uptimeSeconds,
        String uptimeHuman,
        String reason            // non-null when /proc/uptime couldn't be read
) implements HealthCheckEvent {

    public static HostUptimeEvent ok(String target, HealthState state, long uptimeSeconds, String uptimeHuman) {
        return new HostUptimeEvent(
                NwEvent.newEventId(),
                Instant.now(),
                CorePluginDescriptor.PLUGIN_ID,
                CorePluginDescriptor.PLUGIN_VERSION,
                state, target,
                uptimeSeconds, uptimeHuman, null);
    }

    public static HostUptimeEvent unreadable(String target, String reason) {
        return new HostUptimeEvent(
                NwEvent.newEventId(),
                Instant.now(),
                CorePluginDescriptor.PLUGIN_ID,
                CorePluginDescriptor.PLUGIN_VERSION,
                HealthState.UNKNOWN, target,
                0L, null, reason);
    }
}
