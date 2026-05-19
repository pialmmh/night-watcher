package com.tb.nw.core;

import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.PluginEntity;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Built-in "plugin" for entities produced by nw-core itself — the
 * {@code host.uptime} probe and the static facet detector.
 *
 * <p>This is a real {@link PluginDescriptor} so {@link ObservationCache}
 * treats core observations the same way it treats plugin observations:
 * version-stamped, version-checked at the deserialization boundary.</p>
 */
@ApplicationScoped
public class CorePluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_ID = "nw-core";
    public static final String PLUGIN_VERSION = "1.0.0";

    @Override public String pluginId() { return PLUGIN_ID; }
    @Override public String pluginVersion() { return PLUGIN_VERSION; }
    @Override public String serviceType() { return "core"; }
    @Override public Class<? extends PluginEntity> observationDetailType() { return HostUptimeDetail.class; }
    /** Core does not ship action payloads. */
    @Override public Class<? extends PluginEntity> actionPayloadType() { return HostUptimeDetail.class; }
}
