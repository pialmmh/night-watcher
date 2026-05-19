package com.tb.nw.spi;

/**
 * Strongly-typed node identifier. The string value matches the agent's
 * etcd member name and the SPIFFE workload id.
 */
public record NodeId(String value) {
    public static NodeId of(String s) { return new NodeId(s); }
    @Override public String toString() { return value; }
}
