package com.tb.nw.fabric.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface FabricKV {

    Optional<byte[]> get(String key);

    List<KeyValue> list(String prefix);

    void put(String key, byte[] value);

    void put(String key, byte[] value, Lease lease);

    void delete(String key);

    Watch watch(String prefix, Consumer<KeyEvent> handler);

    record KeyValue(String key, byte[] value) {}

    enum EventType { PUT, DELETE }

    record KeyEvent(EventType type, String key, byte[] value) {}

    interface Watch extends AutoCloseable {
        @Override void close();
    }
}
