package com.tb.nw.core.coordinator.events;

/** Fired when the fence-master phase completes (real or simulated). */
public record MasterFenced(String formerMaster) {}
