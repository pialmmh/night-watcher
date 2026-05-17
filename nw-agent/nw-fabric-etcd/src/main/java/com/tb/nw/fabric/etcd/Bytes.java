package com.tb.nw.fabric.etcd;

import io.etcd.jetcd.ByteSequence;

import java.nio.charset.StandardCharsets;

/** Compact conversion helpers between Java types and jetcd's ByteSequence. */
final class Bytes {
    private Bytes() {}

    static ByteSequence of(String s) {
        return ByteSequence.from(s, StandardCharsets.UTF_8);
    }

    static ByteSequence of(byte[] b) {
        return ByteSequence.from(b);
    }

    static String str(ByteSequence bs) {
        return bs.toString(StandardCharsets.UTF_8);
    }

    static byte[] raw(ByteSequence bs) {
        return bs.getBytes();
    }
}
