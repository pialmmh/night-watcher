package com.tb.nw.plugins.mock.events;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.spi.api.HealthCheckEvent;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * The mock plugin's single health event — what any of its three probes saw
 * when it read the target's state file. The factory stamps plugin identity;
 * the framework's identity gate checks it before the typed parse, exactly as
 * for a real plugin.
 */
public record MockHealth(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        HealthState healthState,
        String target,

        /* what the file said */
        String rawState,     // fast | degraded | dead | fenced | absent
        String role,         // master | slave | null (no role file)
        boolean fenced,
        String note
) implements HealthCheckEvent {

    public static MockHealth of(HealthState state, String target,
                                String rawState, String role, boolean fenced, String note) {
        return new MockHealth(
                NwEvent.newEventId(),
                Instant.now(),
                MockPluginDescriptor.PLUGIN_ID,
                MockPluginDescriptor.PLUGIN_VERSION,
                state, target,
                rawState, role, fenced, note);
    }
}
