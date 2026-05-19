package com.tb.nw.plugins.mysql;

import com.tb.nw.plugins.mysql.entities.MysqlActionPayload;
import com.tb.nw.plugins.mysql.entities.MysqlObservationDetail;
import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.PluginEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

/**
 * Single source of truth for the nw-mysql plugin's identity. Every typed
 * entity this plugin emits stamps its {@link #PLUGIN_ID} and
 * {@link #PLUGIN_VERSION}; the framework refuses to consume entities whose
 * stamp does not match the values here.
 *
 * <p>Version bumps require a clean rebuild — the framework does not run
 * compatibility shims for older versions of the same plugin id.</p>
 */
@ApplicationScoped
public class MysqlPluginDescriptor implements PluginDescriptor {

    public static final String PLUGIN_ID = "nw-mysql";
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final String SERVICE_TYPE = "mysql";

    @Override public String pluginId() { return PLUGIN_ID; }
    @Override public String pluginVersion() { return PLUGIN_VERSION; }
    @Override public String serviceType() { return SERVICE_TYPE; }
    @Override public Class<? extends PluginEntity> observationDetailType() { return MysqlObservationDetail.class; }
    @Override public Class<? extends PluginEntity> actionPayloadType() { return MysqlActionPayload.class; }
}
