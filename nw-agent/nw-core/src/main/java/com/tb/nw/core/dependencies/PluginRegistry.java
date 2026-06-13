package com.tb.nw.core.dependencies;

import com.tb.nw.core.cache.api.ObservationCache;
import com.tb.nw.core.dispatch.api.Dispatcher;
import com.tb.nw.core.door.api.ActionEndpoint;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The agent's single view of "which plugins are loaded here". Every inbound
 * boundary (ObservationCache, ActionEndpoint, Dispatcher result parsing)
 * consults this registry for the identity gate + the class whitelists.
 */
@ApplicationScoped
public class PluginRegistry {

    private static final Logger LOG = Logger.getLogger(PluginRegistry.class);

    @Inject Instance<PluginDescriptor> descriptors;

    private final Map<String, PluginDescriptor> byId = new HashMap<>();

    @PostConstruct
    void init() {
        for (PluginDescriptor d : descriptors) {
            byId.put(d.pluginId(), d);
        }
        LOG.infof("PluginRegistry: %d descriptors registered: %s", byId.size(), byId.keySet());
    }

    /** Descriptor for a plugin id, or null when not loaded on this agent. */
    public PluginDescriptor byId(String pluginId) {
        return byId.get(pluginId);
    }

    public Collection<PluginDescriptor> all() {
        return List.copyOf(byId.values());
    }

    /** True when the (pluginId, pluginVersion) pair matches a locally loaded plugin exactly. */
    public boolean versionMatches(String pluginId, String pluginVersion) {
        PluginDescriptor d = byId.get(pluginId);
        return d != null && d.pluginVersion().equals(pluginVersion);
    }

    /** Whitelist lookup: the command class name must be declared by the plugin. */
    public Optional<Class<? extends CommandEvent>> commandClass(PluginDescriptor d, String fqcn) {
        return d.commandEventTypes().stream()
                .filter(c -> c.getName().equals(fqcn))
                .findFirst();
    }

    /** Whitelist lookup: the result class name must be declared by the plugin. */
    public Optional<Class<? extends CommandResultEvent>> resultClass(PluginDescriptor d, String fqcn) {
        return d.resultEventTypes().stream()
                .filter(c -> c.getName().equals(fqcn))
                .findFirst();
    }
}
