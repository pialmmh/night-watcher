package com.tb.nw.fabric.etcd;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricKV;
import com.tb.nw.fabric.api.FabricLease;
import com.tb.nw.fabric.api.FabricLock;
import com.tb.nw.fabric.api.FabricTxn;
import io.etcd.jetcd.Client;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;

/**
 * Concrete Fabric implementation backed by an etcd cluster. Owns the jetcd
 * Client's lifecycle; produces sub-interfaces that wrap clientv3 operations.
 *
 * Thread-safe. One instance per agent.
 */
public final class EtcdFabric implements Fabric, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(EtcdFabric.class);

    private final List<String> endpoints;
    private final Client client;
    private final EtcdFabricKV kv;
    private final EtcdFabricLease leases;
    private final EtcdFabricLock locks;
    private final EtcdFabricTxn txns;

    /** Convenience for the single-endpoint case (smoke tests, dev). */
    public EtcdFabric(String endpoint) {
        this(List.of(endpoint));
    }

    /**
     * Multi-endpoint constructor. jetcd will round-robin requests across the
     * supplied endpoints and survive a single peer being unreachable.
     */
    public EtcdFabric(List<String> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("EtcdFabric requires at least one endpoint");
        }
        this.endpoints = List.copyOf(endpoints);
        this.client = buildClient(this.endpoints);
        this.kv = new EtcdFabricKV(client);
        this.leases = new EtcdFabricLease(client);
        this.locks = new EtcdFabricLock(client);
        this.txns = new EtcdFabricTxn(client);
        LOG.infof("EtcdFabric initialized against %d endpoint(s): %s",
                this.endpoints.size(), this.endpoints);
    }

    public List<String> endpoints() { return endpoints; }

    public boolean ping() {
        return tryStatus();
    }

    @Override public FabricKV kv() { return kv; }
    @Override public FabricLease leases() { return leases; }
    @Override public FabricLock locks() { return locks; }
    @Override public FabricTxn txns() { return txns; }

    @Override public void close() {
        try {
            client.close();
        } catch (Exception e) {
            LOG.warnf(e, "error closing etcd client");
        }
    }

    // ── helpers ──

    private static Client buildClient(List<String> endpoints) {
        return Client.builder()
                .endpoints(endpoints.toArray(new String[0]))
                .connectTimeout(Duration.ofSeconds(5))
                .keepaliveTime(Duration.ofSeconds(30))
                .build();
    }

    private boolean tryStatus() {
        try {
            client.getKVClient().get(Bytes.of("__nw_ping__"))
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            LOG.debugf("etcd ping failed: %s", e.getMessage());
            return false;
        }
    }
}
