package com.tb.nw.core;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricLease;
import com.tb.nw.fabric.api.Lease;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the single etcd lease used by every per-agent KV entry — heartbeat,
 * facets, future observations. When the agent dies the lease lapses and
 * every key tied to it is cleaned up by etcd automatically.
 */
@ApplicationScoped
public class AgentLease {

    private static final Logger LOG = Logger.getLogger(AgentLease.class);

    @Inject AgentConfig cfg;
    @Inject Fabric fabric;

    private final AtomicReference<Lease> lease = new AtomicReference<>();
    private final AtomicReference<FabricLease.KeepAlive> keepAlive = new AtomicReference<>();

    /** Returns the current agent lease, granting one if absent. Throws on fabric outage. */
    public Lease current() {
        Lease l = lease.get();
        return l != null ? l : grantNew();
    }

    private synchronized Lease grantNew() {
        Lease existing = lease.get();
        if (existing != null) return existing;
        Lease fresh = fabric.leases().grant(Duration.ofSeconds(cfg.heartbeatTtlSec()));
        keepAlive.set(fabric.leases().keepAlive(fresh));
        lease.set(fresh);
        LOG.infof("agent lease granted: id=%d ttl=%ds", fresh.id(), cfg.heartbeatTtlSec());
        return fresh;
    }

    /** Best-effort: if the lease appears dead (after fabric reconnect, etc), drop it so the next call grants a new one. */
    public void reset() {
        FabricLease.KeepAlive ka = keepAlive.getAndSet(null);
        if (ka != null) ka.close();
        lease.set(null);
    }

    @PreDestroy
    void shutdown() {
        try {
            FabricLease.KeepAlive ka = keepAlive.get();
            if (ka != null) ka.close();
            Lease l = lease.get();
            if (l != null) fabric.leases().revoke(l);
        } catch (FabricException e) {
            LOG.debugf("lease shutdown error: %s", e.getMessage());
        }
    }
}
