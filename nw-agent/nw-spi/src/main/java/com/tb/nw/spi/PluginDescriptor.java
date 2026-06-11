package com.tb.nw.spi;

import java.util.Set;

/**
 * Plugin-level metadata exposed by every plugin module.
 *
 * <p>Each plugin ships exactly one {@code PluginDescriptor} implementation
 * (typically a CDI {@code @ApplicationScoped} bean or a public singleton).
 * The framework consults it to:</p>
 *
 * <ul>
 *   <li>Validate inbound {@link NwEvent} envelopes — refuse anything whose
 *       {@code pluginId} / {@code pluginVersion} doesn't match.</li>
 *   <li>Resolve the concrete {@link Class} of typed health-check events
 *       and command events when deserializing from etcd / gRPC.</li>
 * </ul>
 *
 * <p>Implementations must be immutable and the values must be compile-time
 * constants — they are the source of truth for what this plugin <em>is</em>.</p>
 */
public interface PluginDescriptor {

    /** Stable identifier, e.g. {@code "nw-mysql"}. Lower-case, hyphen-separated. */
    String pluginId();

    /**
     * Semver-shaped version string, e.g. {@code "1.0.0"}. A change in this
     * value must come with a clean rebuild + redeploy — same plugin id
     * with a different version is a different artifact, never a compatibility
     * shim.
     */
    String pluginVersion();

    /** Plugin's service vocabulary, e.g. {@code "mysql"}. Drives Aggregator / Selector / PlanGenerator dispatch. */
    String serviceType();

    /**
     * The single {@link HealthCheckEvent} subtype this plugin's probes emit.
     * Used by {@code ObservationCache} to deserialize inbound observations
     * back into the right typed body.
     *
     * <p>A plugin currently has one health-event shape. If a plugin grows
     * multiple distinct probe outputs, define a sealed plugin marker
     * (e.g. {@code MySqlHealthEvent}) and return that here.</p>
     */
    Class<? extends HealthCheckEvent> healthEventType();

    /**
     * The set of {@link CommandEvent} subtypes this plugin's actions consume.
     * Used by the Dispatcher / ActionEndpoint to deserialize inbound command
     * payloads by their declared kind.
     */
    Set<Class<? extends CommandEvent>> commandEventTypes();

    /**
     * The set of {@link CommandResultEvent} subtypes this plugin's actions
     * return. Used by the Dispatcher to deserialize result payloads coming
     * back from a target agent — same whitelist discipline as commands.
     * Default empty for plugins that ship no actions.
     */
    default Set<Class<? extends CommandResultEvent>> resultEventTypes() {
        return Set.of();
    }
}
