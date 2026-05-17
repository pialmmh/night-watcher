package com.tb.nw.core;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

/** Config slice for the StaticFacetDetector. Kept separate so plugins don't depend on AgentConfig. */
@ConfigMapping(prefix = "nw.agent")
public interface StaticFacetsConfig {

    /** Comma-separated facet names. Empty means no static facets — defer to plugin detectors. */
    Optional<String> staticFacets();
}
