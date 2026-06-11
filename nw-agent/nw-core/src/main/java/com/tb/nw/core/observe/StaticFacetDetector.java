package com.tb.nw.core.observe;

import com.tb.nw.core.CorePluginDescriptor;

import com.tb.nw.spi.FacetDetector;
import com.tb.nw.spi.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads facets from config (<em>nw.agent.static-facets</em>, comma-separated).
 * Useful for early bring-up and tests before plugin-supplied detectors are wired.
 * Service plugins (nw-mysql, etc.) ship their own detectors that do real probing.
 */
@ApplicationScoped
public class StaticFacetDetector implements FacetDetector {

    private static final Set<String> KNOWN_STATIC_FACETS = Set.of(
            "mysql-master-here", "mysql-slave-here", "mysql-client-here",
            "mysql-binary-present", "mysql-running-here",
            "postgres-master-here", "postgres-slave-here", "postgres-client-here",
            "sigtran-sg-here", "freeswitch-here",
            "witness"
    );

    @Inject StaticFacetsConfig cfg;
    @Inject CorePluginDescriptor descriptor;

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override public Set<String> declaredFacets() {
        return KNOWN_STATIC_FACETS;
    }

    @Override public Set<String> detect() {
        String raw = cfg.staticFacets().orElse("");
        if (raw.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String s : raw.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}
