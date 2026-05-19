package com.tb.nw.spi;

import java.util.List;
import java.util.Map;

/**
 * Cluster snapshot handed to the {@link FailoverPlanGenerator}. Carries
 * topology + the recent observation set so the generator can pick the right
 * step order.
 *
 * @param <T> plugin-specific observation detail
 */
public record ClusterView<T extends PluginEntity>(
        String cluster,
        ClusterType clusterType,
        Map<String, List<String>> rolesByNode,
        List<Observation<T>> recentObservations
) {
    public ClusterTopology topology() {
        return new ClusterTopology(cluster, clusterType, rolesByNode);
    }
}
