package com.tb.nw.core;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable but small piece of agent runtime state, kept here so the status
 * endpoint and the heartbeat task can share it cleanly without ad-hoc statics.
 */
@ApplicationScoped
public class AgentState {

    private final AtomicReference<Status> status = new AtomicReference<>(Status.JOINING);
    private final AtomicReference<Instant> lastHeartbeatOk = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public enum Status { JOINING, ALIVE, DRAINING, ERROR }

    public Status status() { return status.get(); }

    public void markAlive() {
        status.set(Status.ALIVE);
        lastError.set(null);
    }

    public void markError(String reason) {
        status.set(Status.ERROR);
        lastError.set(reason);
    }

    public void markDraining() { status.set(Status.DRAINING); }

    public void recordHeartbeatOk() { lastHeartbeatOk.set(Instant.now()); }

    public Instant lastHeartbeatOk() { return lastHeartbeatOk.get(); }

    public String lastError() { return lastError.get(); }
}
