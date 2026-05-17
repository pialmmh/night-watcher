package com.tb.nw.fabric.etcd;

import com.tb.nw.fabric.api.FabricLock;
import io.etcd.jetcd.Client;

import java.util.function.Consumer;

/**
 * NOTE: Application-level Coordinator election uses jetcd's Election API,
 * which requires a session+lease lifecycle that's a few moving parts. This
 * stub is here so the Fabric contract is complete and downstream code compiles;
 * the real implementation lands when the Coordinator component is added.
 */
final class EtcdFabricLock implements FabricLock {

    private final Client client;

    EtcdFabricLock(Client client) {
        this.client = client;
    }

    @Override public Election campaign(String path, String selfIdentity) {
        throw new UnsupportedOperationException("EtcdFabricLock.campaign not yet implemented");
    }

    @Override public void resign(Election election) {
        throw new UnsupportedOperationException("EtcdFabricLock.resign not yet implemented");
    }

    @Override public Observation observeLeader(String path, Consumer<String> onLeaderChange) {
        throw new UnsupportedOperationException("EtcdFabricLock.observeLeader not yet implemented");
    }
}
