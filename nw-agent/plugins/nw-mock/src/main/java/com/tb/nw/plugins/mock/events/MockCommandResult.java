package com.tb.nw.plugins.mock.events;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.spi.CommandResultEvent;
import com.tb.nw.spi.NwEvent;

import java.time.Instant;
import java.util.Map;

/**
 * One result record serves both mock commands — {@code commandKind}
 * discriminates, {@code detail} carries the after-state of the touched files.
 */
public record MockCommandResult(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        String inReplyToEventId,
        boolean ok,
        String reason,
        String commandKind,
        Map<String, String> detail
) implements CommandResultEvent {

    public static MockCommandResult ok(String inReplyToEventId, String commandKind, Map<String, String> detail) {
        return new MockCommandResult(
                NwEvent.newEventId(), Instant.now(),
                MockPluginDescriptor.PLUGIN_ID, MockPluginDescriptor.PLUGIN_VERSION,
                inReplyToEventId, true, "ok",
                commandKind, detail == null ? Map.of() : Map.copyOf(detail));
    }

    public static MockCommandResult failure(String inReplyToEventId, String commandKind, String reason) {
        return new MockCommandResult(
                NwEvent.newEventId(), Instant.now(),
                MockPluginDescriptor.PLUGIN_ID, MockPluginDescriptor.PLUGIN_VERSION,
                inReplyToEventId, false, reason,
                commandKind, Map.of());
    }
}
