package com.tb.nw.fabric.api;

import java.time.Duration;

public interface FabricLease {

    Lease grant(Duration ttl);

    /** Start a self-renewing keep-alive on this lease. The handle controls cancellation. */
    KeepAlive keepAlive(Lease lease);

    void revoke(Lease lease);

    interface KeepAlive extends AutoCloseable {
        @Override void close();
    }
}
