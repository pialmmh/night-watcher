package com.tb.nw.fabric.api;

/** Opaque lease handle returned by FabricLease.grant(). */
public interface Lease {
    long id();
}
