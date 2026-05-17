package com.tb.nw.fabric.etcd;

import com.tb.nw.fabric.api.Lease;

record EtcdLease(long id) implements Lease {}
