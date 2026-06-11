package com.tb.nw.testkit;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricKV;
import com.tb.nw.fabric.api.FabricLease;
import com.tb.nw.fabric.api.FabricLock;
import com.tb.nw.fabric.api.FabricTxn;
import com.tb.nw.fabric.api.Lease;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory Fabric for unit tests: KV with etcd mod-revision semantics
 * (a missing key compares as revision 0 — the create-if-absent idiom),
 * manually-expirable leases, conditional txns, synchronous watch fan-out.
 *
 * <p>{@link #expireLease(long)} deletes every key bound to the lease and
 * fires DELETE watch events — the "agent died" simulation.</p>
 */
public final class FakeFabric implements Fabric {

    record Entry(byte[] value, long modRevision, Long leaseId) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Set<Long> liveLeases = new HashSet<>();
    private final AtomicLong revision = new AtomicLong(0);
    private final AtomicLong leaseIds = new AtomicLong(100);
    private final List<WatchReg> watches = new CopyOnWriteArrayList<>();

    record WatchReg(String prefix, Consumer<FabricKV.KeyEvent> handler) {}

    private final FabricKV kv = new FabricKV() {
        @Override public Optional<byte[]> get(String key) {
            Entry e = store.get(key);
            return e == null ? Optional.empty() : Optional.of(e.value());
        }
        @Override public List<KeyValue> list(String prefix) {
            List<KeyValue> out = new ArrayList<>();
            store.forEach((k, e) -> { if (k.startsWith(prefix)) out.add(new KeyValue(k, e.value())); });
            return out;
        }
        @Override public void put(String key, byte[] value) { doPut(key, value, null); }
        @Override public void put(String key, byte[] value, Lease lease) { doPut(key, value, lease.id()); }
        @Override public void delete(String key) { doDelete(key); }
        @Override public Watch watch(String prefix, Consumer<KeyEvent> handler) {
            WatchReg reg = new WatchReg(prefix, handler);
            watches.add(reg);
            return () -> watches.remove(reg);
        }
    };

    private void doPut(String key, byte[] value, Long leaseId) {
        store.put(key, new Entry(value, revision.incrementAndGet(), leaseId));
        fire(new FabricKV.KeyEvent(FabricKV.EventType.PUT, key, value));
    }

    private void doDelete(String key) {
        if (store.remove(key) != null) {
            fire(new FabricKV.KeyEvent(FabricKV.EventType.DELETE, key, null));
        }
    }

    private void fire(FabricKV.KeyEvent ev) {
        for (WatchReg w : watches) {
            if (ev.key().startsWith(w.prefix())) w.handler().accept(ev);
        }
    }

    private final FabricLease leases = new FabricLease() {
        @Override public Lease grant(Duration ttl) {
            long id = leaseIds.incrementAndGet();
            liveLeases.add(id);
            return () -> id;
        }
        @Override public KeepAlive keepAlive(Lease lease) { return () -> {}; }
        @Override public void revoke(Lease lease) { expireLease(lease.id()); }
    };

    /** Simulate lease expiry: bound keys vanish, watchers see DELETE. */
    public void expireLease(long leaseId) {
        liveLeases.remove(leaseId);
        for (String key : List.copyOf(store.keySet())) {
            Entry e = store.get(key);
            if (e != null && e.leaseId() != null && e.leaseId() == leaseId) doDelete(key);
        }
    }

    private final FabricTxn txns = () -> new FabricTxn.TxnBuilder() {
        record Cond(String key, Long modRev, byte[] value) {}
        record Op(String key, byte[] value, Long leaseId, boolean delete) {}
        private final List<Cond> conds = new ArrayList<>();
        private final List<Op> thens = new ArrayList<>();
        private final List<Op> elses = new ArrayList<>();

        @Override public FabricTxn.TxnBuilder ifModRevisionEquals(String key, long rev) {
            conds.add(new Cond(key, rev, null)); return this;
        }
        @Override public FabricTxn.TxnBuilder ifValueEquals(String key, byte[] value) {
            conds.add(new Cond(key, null, value)); return this;
        }
        @Override public FabricTxn.TxnBuilder thenPut(String key, byte[] value) {
            thens.add(new Op(key, value, null, false)); return this;
        }
        @Override public FabricTxn.TxnBuilder thenPut(String key, byte[] value, Lease lease) {
            thens.add(new Op(key, value, lease.id(), false)); return this;
        }
        @Override public FabricTxn.TxnBuilder thenDelete(String key) {
            thens.add(new Op(key, null, null, true)); return this;
        }
        @Override public FabricTxn.TxnBuilder elsePut(String key, byte[] value) {
            elses.add(new Op(key, value, null, false)); return this;
        }
        @Override public FabricTxn.TxnResponse commit() {
            boolean ok = conds.stream().allMatch(this::holds);
            for (Op op : ok ? thens : elses) {
                if (op.delete()) doDelete(op.key());
                else doPut(op.key(), op.value(), op.leaseId());
            }
            boolean succeeded = ok;
            return new FabricTxn.TxnResponse() {
                @Override public boolean succeeded() { return succeeded; }
                @Override public List<FabricKV.KeyValue> reads() { return List.of(); }
            };
        }
        private boolean holds(Cond c) {
            Entry e = store.get(c.key());
            if (c.modRev() != null) {
                long actual = e == null ? 0 : e.modRevision();   // etcd: absent key = rev 0
                return actual == c.modRev();
            }
            return e != null && java.util.Arrays.equals(e.value(), c.value());
        }
    };

    @Override public FabricKV kv() { return kv; }
    @Override public FabricLease leases() { return leases; }
    @Override public FabricLock locks() { throw new UnsupportedOperationException("unused by design"); }
    @Override public FabricTxn txns() { return txns; }
}
