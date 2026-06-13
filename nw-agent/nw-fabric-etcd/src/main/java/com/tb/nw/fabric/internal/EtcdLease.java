package com.tb.nw.fabric.internal;

import com.tb.nw.fabric.api.Lease;

record EtcdLease(long id) implements Lease {}
