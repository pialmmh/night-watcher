package com.tb.nw.spi;

import java.util.List;
import java.util.Map;

/**
 * Side information the CandidateSelector may need beyond the observation set:
 * current roles, maintenance flags, tiebreaker policy from cluster config.
 */
public record SelectionContext(
        String cluster,
        ClusterType clusterType,
        Map<String, List<String>> rolesByNode,
        List<String> tiebreakers
) {}
