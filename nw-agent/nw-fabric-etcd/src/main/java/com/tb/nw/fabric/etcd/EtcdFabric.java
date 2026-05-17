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

/**
 * Concrete Fabric implementation backed by an etcd cluster. Owns the jetcd
 * Client's lifecycle; produces sub-interfaces that wrap clientv3 operations.
 *
 * Thread-safe. One instance per agent.
 */
public final class EtcdFabric implements Fabric, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(EtcdFabric.class);

    private final String endpoint;
    private final Client client;
    private final EtcdFabricKV kv;
    private final EtcdFabricLease leases;
    private final EtcdFabricLock locks;
    private final EtcdFabricTxn txns;

    public EtcdFabric(String endpoint) {
        this.endpoint = endpoint;
        this.client = buildClient(endpoint);
        this.kv = new EtcdFabricKV(client);
        this.leases = new EtcdFabricLease(client);
        this.locks = new EtcdFabricLock(client);
        this.txns = new EtcdFabricTxn(client);
        LOG.infof("EtcdFabric initialized against %s", endpoint);
    }

    public String endpoint() { return endpoint; }

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

    private static Client buildClient(String endpoint) {
        return Client.builder()
                .endpoints(endpoint)
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
