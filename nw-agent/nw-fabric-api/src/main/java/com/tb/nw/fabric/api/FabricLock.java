package com.tb.nw.fabric.api;

import java.util.function.Consumer;

public interface FabricLock {

    /** Block until won. Returns an Election handle backed by a session lease. */
    Election campaign(String path, String selfIdentity);

    /** Voluntarily release the election. */
    void resign(Election election);

    /** Observe leader changes on a path without participating. */
    Observation observeLeader(String path, Consumer<String> onLeaderChange);

    interface Election extends AutoCloseable {
        String currentLeader();
        @Override void close();
    }

    interface Observation extends AutoCloseable {
        @Override void close();
    }
}
