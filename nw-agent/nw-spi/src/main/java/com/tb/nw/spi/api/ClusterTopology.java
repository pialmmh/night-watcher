package com.tb.nw.spi.api;

import java.util.List;
import java.util.Map;

/**
 * Pure topology snapshot: which nodes hold which roles right now. Inputs to
 * ClusterCondition predicates ("master_elected", "quorum_of_slaves", …).
 *
 * No observations here — keep this slim so predicate evaluation is cheap and
 * deterministic. Observation-rich decisions belong to the Resolver pipeline.
 */
public record ClusterTopology(
        String cluster,
        ClusterType clusterType,
        Map<String, List<String>> rolesByNode
) {}
