package com.tb.nw.spi;

import java.util.Set;

/**
 * Detects what's locally present on this host. Output is the facet set used
 * by the activation engine to decide which investigators arm.
 */
public interface FacetDetector {

    /** All facets this detector can ever produce. Used at startup for validation. */
    Set<String> declaredFacets();

    /** Runs the detection probes; returns the subset of declared facets that hold now. */
    Set<String> detect();
}
