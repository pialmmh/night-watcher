package com.tb.nw.spi.api;

/**
 * Marker for every typed record that flows across plugin boundaries.
 *
 * <p>The agent enforces this contract end-to-end. Anything that crosses a
 * pluggable component boundary — observations published to etcd, the
 * detail body inside them, action payloads, cluster verdicts — implements
 * this marker so the framework can:</p>
 *
 * <ol>
 *   <li>Stamp the producing plugin's identity on every entity at construction.</li>
 *   <li>Reject inbound entities at the deserialization boundary when their
 *       {@code pluginId} / {@code pluginVersion} does not match a locally
 *       loaded plugin's descriptor.</li>
 * </ol>
 *
 * <h2>Versioning rule</h2>
 *
 * There is no entity-level schema versioning. The plugin's version is the
 * version of every entity it produces. When the plugin's data shape changes,
 * the plugin gets a new version (and, if cohabiting with the old, a new
 * {@code pluginId}). Mismatched versions are dropped — the framework does
 * not deserialize, does not score, does not act on them.
 *
 * <p>Implementations are typically Java {@code record} classes with the two
 * methods explicitly declared (records auto-implement accessors only when the
 * names match the component names — so the record must include
 * {@code pluginId} and {@code pluginVersion} components, or override these
 * accessors manually).</p>
 */
public interface PluginEntity {

    /** Producing plugin's stable identifier, e.g. {@code "nw-mysql"}. */
    String pluginId();

    /** Producing plugin's version string, e.g. {@code "1.0.0"}. */
    String pluginVersion();
}
