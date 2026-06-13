package com.tb.nw.spi.api;

import java.util.Set;

/**
 * Detects what's locally present on this host. Output is the facet set used
 * by the activation engine to decide which investigators arm.
 *
 * <p>Facet identifiers are plain strings — small, stable, copy-pasteable.
 * The plugin that owns a facet vocabulary is responsible for documenting
 * it in its manifest. There is no typed envelope here because facets are
 * a set of stable identifiers, not a record of data; the version-stamp
 * lives on the plugin's other entities (observation detail, action payload).</p>
 */
public interface FacetDetector {

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /** All facets this detector can ever produce. Used at startup for validation. */
    Set<String> declaredFacets();

    /** Runs the detection probes; returns the subset of declared facets that hold now. */
    Set<String> detect();
}
