package com.tb.nw.spi;

/**
 * Thrown (or logged + dropped, depending on context) when an inbound
 * {@link PluginEntity} carries {@code pluginId} / {@code pluginVersion}
 * that does not match any locally loaded plugin.
 *
 * <p>The framework's standard response is <em>refuse and log</em> rather
 * than throw — but plugin code that handles entities directly is welcome to
 * raise this when it detects a mismatch and wants the dispatch path to
 * surface the failure.</p>
 */
public class PluginVersionMismatchException extends RuntimeException {

    private final String expectedPluginId;
    private final String expectedPluginVersion;
    private final String actualPluginId;
    private final String actualPluginVersion;

    public PluginVersionMismatchException(String expectedPluginId, String expectedPluginVersion,
                                          String actualPluginId, String actualPluginVersion) {
        super(String.format(
                "plugin version mismatch: expected %s@%s but got %s@%s",
                expectedPluginId, expectedPluginVersion,
                actualPluginId, actualPluginVersion));
        this.expectedPluginId = expectedPluginId;
        this.expectedPluginVersion = expectedPluginVersion;
        this.actualPluginId = actualPluginId;
        this.actualPluginVersion = actualPluginVersion;
    }

    public String expectedPluginId()      { return expectedPluginId; }
    public String expectedPluginVersion() { return expectedPluginVersion; }
    public String actualPluginId()        { return actualPluginId; }
    public String actualPluginVersion()   { return actualPluginVersion; }
}
