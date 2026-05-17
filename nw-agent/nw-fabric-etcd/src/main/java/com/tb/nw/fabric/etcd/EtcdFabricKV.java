package com.tb.nw.fabric.etcd;

import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricKV;
import com.tb.nw.fabric.api.Lease;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

final class EtcdFabricKV implements FabricKV {

    private final Client client;

    EtcdFabricKV(Client client) {
        this.client = client;
    }

    @Override public Optional<byte[]> get(String key) {
        GetResponse r = await(client.getKVClient().get(Bytes.of(key)));
        return r.getKvs().isEmpty()
                ? Optional.empty()
                : Optional.of(Bytes.raw(r.getKvs().get(0).getValue()));
    }

    @Override public List<KeyValue> list(String prefix) {
        GetOption opt = GetOption.builder().isPrefix(true).build();
        GetResponse r = await(client.getKVClient().get(Bytes.of(prefix), opt));
        return r.getKvs().stream().map(this::convert).toList();
    }

    @Override public void put(String key, byte[] value) {
        await(client.getKVClient().put(Bytes.of(key), Bytes.of(value)));
    }

    @Override public void put(String key, byte[] value, Lease lease) {
        PutOption opt = PutOption.builder().withLeaseId(lease.id()).build();
        await(client.getKVClient().put(Bytes.of(key), Bytes.of(value), opt));
    }

    @Override public void delete(String key) {
        await(client.getKVClient().delete(Bytes.of(key)));
    }

    @Override public Watch watch(String prefix, Consumer<KeyEvent> handler) {
        WatchOption opt = WatchOption.builder().isPrefix(true).build();
        io.etcd.jetcd.Watch.Watcher watcher = client.getWatchClient().watch(
                Bytes.of(prefix), opt,
                response -> dispatchEvents(response.getEvents(), handler));
        return watcher::close;
    }

    private void dispatchEvents(List<WatchEvent> events, Consumer<KeyEvent> handler) {
        for (WatchEvent ev : events) {
            handler.accept(toKeyEvent(ev));
        }
    }

    private KeyEvent toKeyEvent(WatchEvent ev) {
        KeyValue kv = convert(ev.getKeyValue());
        EventType type = switch (ev.getEventType()) {
            case PUT -> EventType.PUT;
            case DELETE -> EventType.DELETE;
            default -> EventType.DELETE;
        };
        return new KeyEvent(type, kv.key(), kv.value());
    }

    private KeyValue convert(io.etcd.jetcd.KeyValue jetcdKv) {
        return new KeyValue(Bytes.str(jetcdKv.getKey()), Bytes.raw(jetcdKv.getValue()));
    }

    private <T> T await(java.util.concurrent.Future<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FabricException(FabricException.Kind.INTERNAL, "interrupted", e);
        } catch (ExecutionException e) {
            throw new FabricException(FabricException.Kind.UNAVAILABLE,
                    "etcd op failed: " + e.getCause().getMessage(), e.getCause());
        }
    }
}
