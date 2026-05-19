package com.tb.nw.core;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.etcd.EtcdFabric;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Builds the singleton Fabric instance from agent config. The etcd client
 * lifecycle follows CDI scope — created at first injection, disposed at
 * shutdown.
 */
@ApplicationScoped
public class FabricProducer {

    private static final Logger LOG = Logger.getLogger(FabricProducer.class);

    @Produces
    @Singleton
    public Fabric fabric(AgentConfig cfg) {
        LOG.infof("creating EtcdFabric: endpoints=%s, node=%s", cfg.fabricEndpoints(), cfg.nodeName());
        return new EtcdFabric(cfg.fabricEndpoints());
    }

    public void disposeFabric(@Disposes Fabric fabric) {
        if (fabric instanceof AutoCloseable c) {
            try { c.close(); } catch (Exception e) {
                LOG.warnf(e, "fabric close failed");
            }
        }
    }
}
