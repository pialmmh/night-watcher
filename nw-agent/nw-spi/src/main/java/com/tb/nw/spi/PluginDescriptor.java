package com.tb.nw.spi;

/**
 * Plugin-level metadata exposed by every plugin module.
 *
 * <p>Each plugin ships exactly one {@code PluginDescriptor} implementation
 * (typically a CDI {@code @ApplicationScoped} bean or a public singleton).
 * The framework consults it to:</p>
 *
 * <ul>
 *   <li>Validate inbound {@link PluginEntity} envelopes — refuse anything
 *       whose {@code pluginId} / {@code pluginVersion} doesn't match.</li>
 *   <li>Resolve the concrete {@link Class} for typed observation details
 *       and action payloads when deserializing from etcd / gRPC.</li>
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

    /** The {@link PluginEntity} class this plugin's probes attach as observation detail. */
    Class<? extends PluginEntity> observationDetailType();

    /** The {@link PluginEntity} class this plugin's actions consume as payload. */
    Class<? extends PluginEntity> actionPayloadType();

    /** Plugin's service vocabulary, e.g. {@code "mysql"}. Drives Aggregator + Selector + PlanGenerator dispatch. */
    String serviceType();
}
