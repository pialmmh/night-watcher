package com.tb.nw.spi.api;

import java.util.List;
import java.util.Map;

/**
 * Cluster snapshot handed to the {@link FailoverPlanGenerator}. Carries
 * topology + the recent observation set so the generator can pick the right
 * step order.
 *
 * @param <T> plugin-specific {@link HealthCheckEvent} subtype
 */
public record ClusterView<T extends HealthCheckEvent>(
        String cluster,
        ClusterType clusterType,
        long failoverEpoch,
        Map<String, List<String>> rolesByNode,
        List<Observation<T>> recentObservations
) {
    public ClusterTopology topology() {
        return new ClusterTopology(cluster, clusterType, rolesByNode);
    }
}
