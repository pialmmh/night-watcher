package com.tb.nw.spi.api;

/**
 * Pluggable predicate evaluated by the Activation engine against the cluster
 * topology. Built-in conditions ({@code master_elected}, {@code quorum_of_slaves},
 * {@code has_clients}, {@code not_in_failover}) are augmented by plugin-supplied ones.
 *
 * <p>Implementations must be pure: same topology → same boolean. No typed
 * entities flow through this interface — topology is a framework-owned
 * shape, and the boolean result has no payload to version-stamp.</p>
 */
public interface ClusterCondition {

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /** The condition name referenced from catalog YAML's {@code requires_cluster_conditions}. */
    String id();

    /** Evaluate against the cluster's current role map. */
    boolean evaluate(ClusterTopology topology);
}
