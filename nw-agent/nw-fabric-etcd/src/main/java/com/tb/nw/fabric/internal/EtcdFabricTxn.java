package com.tb.nw.fabric.internal;

import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricKV;
import com.tb.nw.fabric.api.FabricTxn;
import com.tb.nw.fabric.api.Lease;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.PutOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class EtcdFabricTxn implements FabricTxn {

    private final Client client;

    EtcdFabricTxn(Client client) {
        this.client = client;
    }

    @Override public TxnBuilder begin() {
        return new Builder(client);
    }

    private static final class Builder implements TxnBuilder {
        private final Client client;
        private final List<Cmp> compares = new ArrayList<>();
        private final List<Op> thens = new ArrayList<>();
        private final List<Op> elses = new ArrayList<>();

        Builder(Client client) { this.client = client; }

        @Override public TxnBuilder ifModRevisionEquals(String key, long revision) {
            compares.add(new Cmp(Bytes.of(key), Cmp.Op.EQUAL, CmpTarget.modRevision(revision)));
            return this;
        }

        @Override public TxnBuilder ifValueEquals(String key, byte[] value) {
            ByteSequence bs = (value == null) ? Bytes.of("") : Bytes.of(value);
            compares.add(new Cmp(Bytes.of(key), Cmp.Op.EQUAL, CmpTarget.value(bs)));
            return this;
        }

        @Override public TxnBuilder thenPut(String key, byte[] value) {
            thens.add(Op.put(Bytes.of(key), Bytes.of(value), PutOption.DEFAULT));
            return this;
        }

        @Override public TxnBuilder thenPut(String key, byte[] value, Lease lease) {
            PutOption opt = PutOption.builder().withLeaseId(lease.id()).build();
            thens.add(Op.put(Bytes.of(key), Bytes.of(value), opt));
            return this;
        }

        @Override public TxnBuilder thenDelete(String key) {
            thens.add(Op.delete(Bytes.of(key), io.etcd.jetcd.options.DeleteOption.DEFAULT));
            return this;
        }

        @Override public TxnBuilder elsePut(String key, byte[] value) {
            elses.add(Op.put(Bytes.of(key), Bytes.of(value), PutOption.DEFAULT));
            return this;
        }

        @Override public TxnResponseBox commit() {
            try {
                io.etcd.jetcd.kv.TxnResponse r = client.getKVClient().txn()
                        .If(compares.toArray(Cmp[]::new))
                        .Then(thens.toArray(Op[]::new))
                        .Else(elses.toArray(Op[]::new))
                        .commit().get();
                return new TxnResponseBox(r.isSucceeded(), Collections.emptyList());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FabricException(FabricException.Kind.INTERNAL, "interrupted", e);
            } catch (ExecutionException e) {
                throw new FabricException(FabricException.Kind.UNAVAILABLE,
                        "txn commit failed: " + e.getCause().getMessage(), e.getCause());
            }
        }
    }

    /** Returned by commit() — minimal for now; reads are deferred. */
    public record TxnResponseBox(boolean succeeded, List<FabricKV.KeyValue> reads)
            implements FabricTxn.TxnResponse {}
}
