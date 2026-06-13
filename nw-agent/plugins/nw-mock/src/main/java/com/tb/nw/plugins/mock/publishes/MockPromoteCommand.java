package com.tb.nw.plugins.mock.publishes;

import com.tb.nw.plugins.mock.api.MockPluginDescriptor;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Orchestrator → the chosen standby's agent: promote yourself. The mock
 * surgery writes {@code master} into the node's role file and {@code fast}
 * into its state file.
 */
public record MockPromoteCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode
) implements CommandEvent {

    @Override public String commandKind() { return "mock.promote"; }

    public static MockPromoteCommand of(long epoch, String targetNode) {
        return new MockPromoteCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MockPluginDescriptor.PLUGIN_ID,
                MockPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode);
    }
}
