package com.tb.nw.spi.api;

/**
 * Resolver output for one (cluster, target) pair. The Dispatcher acts on
 * SDOWN / ODOWN; UP and DEGRADED keep the cluster in steady state.
 */
public enum Verdict {
    UP,
    DEGRADED,
    SDOWN,
    ODOWN
}
