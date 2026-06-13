package com.tb.nw.plugins.mock.events;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Orchestrator → the dead master's agent: fence yourself. The mock surgery
 * writes {@code fenced} into the node's state file — after which every seat
 * reads the node as refusing connections.
 */
public record MockFenceCommand(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        long failoverEpoch,
        String targetNode
) implements CommandEvent {

    @Override public String commandKind() { return "mock.fence"; }

    public static MockFenceCommand of(long epoch, String targetNode) {
        return new MockFenceCommand(
                NwEvent.newEventId(),
                Instant.now(),
                MockPluginDescriptor.PLUGIN_ID,
                MockPluginDescriptor.PLUGIN_VERSION,
                epoch,
                targetNode);
    }
}
