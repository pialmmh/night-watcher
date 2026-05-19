package com.tb.nw.core.coord.events;

/** Fired when the fence-master phase completes (real or simulated). */
public record MasterFenced(String formerMaster) {}
