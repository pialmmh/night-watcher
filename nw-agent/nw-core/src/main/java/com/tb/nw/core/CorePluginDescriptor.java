package com.tb.nw.core;

import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.core.observe.HostUptimeEvent;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.HealthCheckEvent;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Built-in "plugin" for events produced by nw-core itself — the
 * {@code host.uptime} probe and the static facet detector.
 *
 * <p>This is a real {@link PluginDescriptor} so {@link ObservationCache}
 * treats core events the same way it treats plugin events: version-stamped,
 * version-checked at the deserialization boundary.</p>
 */
@ApplicationScoped
public class CorePluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_ID = "nw-core";
    public static final String PLUGIN_VERSION = "1.0.0";

    @Override public String pluginId()      { return PLUGIN_ID; }
    @Override public String pluginVersion() { return PLUGIN_VERSION; }
    @Override public String serviceType()   { return "core"; }
    @Override public Class<? extends HealthCheckEvent> healthEventType() { return HostUptimeEvent.class; }
    /** Core does not ship its own command events. */
    @Override public Set<Class<? extends CommandEvent>> commandEventTypes() { return Set.of(); }
}
