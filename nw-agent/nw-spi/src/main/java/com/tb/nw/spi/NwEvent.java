package com.tb.nw.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Every domain message that crosses a plugin-actor boundary in night-watcher
 * is an {@code NwEvent}. Probes emit them, the resolver consumes them,
 * orchestrators send commands, target nodes return results.
 *
 * <h2>The contract</h2>
 *
 * <ul>
 *   <li>Inherits {@link PluginEntity} — plugin id + version are stamped on
 *       every event, the framework refuses cross-version traffic.</li>
 *   <li>Adds {@link #eventId} — a stable identifier (typically a UUID
 *       string). Required so command / result events can correlate via
 *       {@link CommandResultEvent#inReplyToEventId}.</li>
 *   <li>Adds {@link #timestamp} — when the producing actor minted the event,
 *       in wall-clock UTC.</li>
 *   <li>Adds {@link #eventType} — the coarse category, derived from which
 *       marker sub-interface the event implements.</li>
 * </ul>
 *
 * <h2>Sub-interfaces, by category</h2>
 *
 * <ul>
 *   <li>{@link HealthCheckEvent} — probe outputs</li>
 *   <li>{@link CommandEvent} — directives to a target node</li>
 *   <li>{@link CommandResultEvent} — outcomes returned by the target</li>
 *   <li>{@link CoordinationEvent} — cross-actor inferences</li>
 *   <li>{@link LifecycleEvent} — SM / agent state transitions</li>
 * </ul>
 *
 * Concrete plugin events typically implement a plugin marker
 * ({@code MySqlNwEvent}) and one of the category markers above.
 */
public interface NwEvent extends PluginEntity {

    /** Stable identifier (UUID string by convention). */
    String eventId();

    /** When the producing actor minted the event. UTC by convention. */
    Instant timestamp();

    /** Coarse event category — derived from the category marker. */
    EventType eventType();

    /** Convenience: generate a fresh UUID-shaped event id. */
    static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
