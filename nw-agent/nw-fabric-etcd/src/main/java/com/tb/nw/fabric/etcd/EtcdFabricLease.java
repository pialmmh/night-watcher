package com.tb.nw.fabric.etcd;

import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricLease;
import com.tb.nw.fabric.api.Lease;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.support.CloseableClient;
import io.etcd.jetcd.support.Observers;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

final class EtcdFabricLease implements FabricLease {

    private final Client client;

    EtcdFabricLease(Client client) {
        this.client = client;
    }

    @Override public Lease grant(Duration ttl) {
        try {
            long id = client.getLeaseClient().grant(ttl.toSeconds()).get().getID();
            return new EtcdLease(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FabricException(FabricException.Kind.INTERNAL, "interrupted", e);
        } catch (ExecutionException e) {
            throw new FabricException(FabricException.Kind.UNAVAILABLE,
                    "lease grant failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override public KeepAlive keepAlive(Lease lease) {
        CloseableClient ka = client.getLeaseClient().keepAlive(
                lease.id(),
                Observers.observer(resp -> {/* heartbeat fired; nothing to do */}));
        return ka::close;
    }

    @Override public void revoke(Lease lease) {
        try {
            client.getLeaseClient().revoke(lease.id()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FabricException(FabricException.Kind.INTERNAL, "interrupted", e);
        } catch (ExecutionException e) {
            throw new FabricException(FabricException.Kind.UNAVAILABLE,
                    "lease revoke failed: " + e.getCause().getMessage(), e.getCause());
        }
    }
}
