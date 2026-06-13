package com.tb.nw.core.coordinator.publishes;

/** Fired when a slave has been promoted to master (real or simulated). */
public record SlavePromoted(String newMaster) {}
