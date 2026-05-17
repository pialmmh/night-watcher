package com.tb.nw.fabric.api;

/**
 * Root accessor for fabric primitives. The agent holds one Fabric instance and
 * injects it where needed. Application code (Resolver, Dispatcher, plugins)
 * does NOT import an etcd client class directly — it goes through this interface.
 */
public interface Fabric {
    FabricKV kv();
    FabricLease leases();
    FabricLock locks();
    FabricTxn txns();
}
