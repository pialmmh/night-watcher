package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.publishes.MySqlCommandEvent;
import com.tb.nw.plugins.mysql.publishes.MySqlCommandResult;
import com.tb.nw.plugins.mysql.publishes.MySqlFenceMasterCommand;
import com.tb.nw.plugins.mysql.publishes.MySqlHealthEvent;
import com.tb.nw.plugins.mysql.publishes.MySqlPromoteSlaveCommand;
import com.tb.nw.plugins.mysql.publishes.MySqlRemoteHealth;
import com.tb.nw.plugins.mysql.publishes.MySqlSetReadOnlyCommand;
import com.tb.nw.plugins.mysql.publishes.MySqlStartReplicaCommand;
import com.tb.nw.plugins.mysql.publishes.MySqlStopReplicaCommand;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.HealthCheckEvent;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Single source of truth for the {@code nw-mysql} plugin's identity. Every
 * typed event this plugin emits stamps its {@link #PLUGIN_ID} and
 * {@link #PLUGIN_VERSION}; the framework refuses to consume events whose
 * stamp does not match the values here.
 *
 * <p>Version bumps require a clean rebuild — the framework runs no
 * compatibility shims for older versions of the same plugin id.</p>
 */
@ApplicationScoped
public class MysqlPluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_ID = "nw-mysql";
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final String SERVICE_TYPE = "mysql";

    @Override public String pluginId()      { return PLUGIN_ID; }
    @Override public String pluginVersion() { return PLUGIN_VERSION; }
    @Override public String serviceType()   { return SERVICE_TYPE; }

    /** Single MySQL health-event shape today. Refines to {@link MySqlHealthEvent} when more probes arrive. */
    @Override public Class<? extends HealthCheckEvent> healthEventType() { return MySqlRemoteHealth.class; }

    /** Every concrete {@link MySqlCommandEvent} subtype this plugin's actions consume. */
    @Override public Set<Class<? extends CommandEvent>> commandEventTypes() {
        return Set.of(
                MySqlPromoteSlaveCommand.class,
                MySqlFenceMasterCommand.class,
                MySqlSetReadOnlyCommand.class,
                MySqlStartReplicaCommand.class,
                MySqlStopReplicaCommand.class);
    }

    /** One result record serves all five commands — the Dispatcher's parse whitelist. */
    @Override public Set<Class<? extends CommandResultEvent>> resultEventTypes() {
        return Set.of(MySqlCommandResult.class);
    }
}
