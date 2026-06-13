package com.tb.nw.plugins.mock.api;

import com.tb.nw.plugins.mock.publishes.MockCommandResult;
import com.tb.nw.plugins.mock.publishes.MockFenceCommand;
import com.tb.nw.plugins.mock.publishes.MockHealth;
import com.tb.nw.plugins.mock.publishes.MockPromoteCommand;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.HealthCheckEvent;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Identity of the {@code nw-mock} plugin — the harness plugin that exercises
 * the full core pipeline against a file-based mock service. Same whitelist
 * discipline as a real plugin: every typed event stamps these constants and
 * every door checks them.
 */
@ApplicationScoped
public class MockPluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_ID = "nw-mock";
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final String SERVICE_TYPE = "mock";

    @Override public String pluginId()      { return PLUGIN_ID; }
    @Override public String pluginVersion() { return PLUGIN_VERSION; }
    @Override public String serviceType()   { return SERVICE_TYPE; }

    @Override public Class<? extends HealthCheckEvent> healthEventType() { return MockHealth.class; }

    @Override public Set<Class<? extends CommandEvent>> commandEventTypes() {
        return Set.of(MockFenceCommand.class, MockPromoteCommand.class);
    }

    @Override public Set<Class<? extends CommandResultEvent>> resultEventTypes() {
        return Set.of(MockCommandResult.class);
    }
}
