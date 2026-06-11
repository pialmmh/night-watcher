package com.tb.nw.spi;

import java.time.Instant;

/**
 * Framework-level refusal result — returned by an agent's ActionEndpoint
 * when a command dies at one of the gates (version, address, epoch, role)
 * BEFORE any plugin action runs, or as the backstop when an action throws.
 *
 * <p>Stamped with the refusing agent's <em>core</em> plugin identity, since
 * the refusal may happen before the command's own plugin is even resolved.
 * The Dispatcher accepts this class from any agent in addition to the
 * command plugin's declared {@link PluginDescriptor#resultEventTypes()}.</p>
 *
 * <p>Reason codes used by the endpoint: {@code version-mismatch} ·
 * {@code unknown-command-class} · {@code wrong-node} · {@code epoch-stale} ·
 * {@code epoch-unverifiable} · {@code role-not-permitted} ·
 * {@code no-action} · {@code action-threw: …}.</p>
 */
public record CommandRefused(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        String inReplyToEventId,
        String reason
) implements CommandResultEvent {

    @Override public boolean ok() { return false; }

    /** Factory — stamps the refusing agent's core identity. */
    public static CommandRefused of(String corePluginId, String corePluginVersion,
                                    String inReplyToEventId, String reason) {
        return new CommandRefused(
                NwEvent.newEventId(),
                Instant.now(),
                corePluginId,
                corePluginVersion,
                inReplyToEventId == null ? "unknown" : inReplyToEventId,
                reason);
    }
}
