package com.tb.nw.core;

import com.tb.nw.spi.FacetDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds every FacetDetector available to the agent — built-ins from nw-core
 * plus any contributed by plugin modules via CDI bean discovery.
 *
 * Plugin authors expose detectors by annotating their classes with CDI scopes
 * (@ApplicationScoped). Quarkus's build-time bean discovery picks them up
 * without reflection — native-image friendly.
 */
@ApplicationScoped
public class FacetRegistry {

    private static final Logger LOG = Logger.getLogger(FacetRegistry.class);

    @Inject Instance<FacetDetector> detectors;

    /** All declared facets across every detector — for validation. */
    public Set<String> allDeclaredFacets() {
        Set<String> out = new HashSet<>();
        for (FacetDetector d : detectors) out.addAll(d.declaredFacets());
        return out;
    }

    /** Run every detector; merge results. Detector failures are logged and skipped. */
    public Set<String> detectAll() {
        Set<String> out = new LinkedHashSet<>();
        for (FacetDetector d : detectors) {
            try {
                out.addAll(d.detect());
            } catch (Exception e) {
                LOG.warnf(e, "facet detector %s failed", d.getClass().getName());
            }
        }
        return out;
    }

    public List<String> detectorClassNames() {
        return detectors.stream().map(d -> d.getClass().getSimpleName()).toList();
    }
}
