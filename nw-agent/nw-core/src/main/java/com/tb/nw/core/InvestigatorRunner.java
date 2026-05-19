package com.tb.nw.core;

import com.tb.nw.spi.ClusterType;
import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.PluginEntity;
import com.tb.nw.spi.ProbeContext;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Tick-driven runner. Discovers every CDI-registered {@code HealthCheck<?>}
 * and invokes it on schedule. Converts each typed {@link HealthReport} into
 * a typed {@link Observation} envelope and hands it to the
 * {@link ObservationPublisher}.
 *
 * <p>For now this is a flat "all checks every tick" model. The Activation
 * engine (future) will filter by predicates and per-investigator schedule.</p>
 *
 * <p>The CDI injection captures every plugin's typed probe via the wildcard
 * bound {@code HealthCheck<? extends PluginEntity>}. The typed detail flows
 * through to the published Observation — Jackson serializes it as the
 * plugin's record shape, and ObservationCache uses the registered
 * PluginDescriptors to deserialize incoming envelopes back into the right
 * typed body.</p>
 */
@Startup
@ApplicationScoped
public class InvestigatorRunner {

    private static final Logger LOG = Logger.getLogger(InvestigatorRunner.class);

    @Inject AgentConfig cfg;
    @Inject ObservationPublisher publisher;
    @Inject FacetPublisher facets;
    @Inject com.tb.nw.fabric.api.Fabric fabric;

    @Inject
    Instance<HealthCheck<? extends PluginEntity>> checks;

    @Scheduled(every = "5s", delayed = "6s")
    public void tick() {
        var currentFacets = facets.snapshot();
        for (HealthCheck<? extends PluginEntity> check : checks) {
            if (notApplicable(check, currentFacets)) continue;
            runOne(check);
        }
    }

    private boolean notApplicable(HealthCheck<? extends PluginEntity> check, java.util.Set<String> currentFacets) {
        var required = check.requiredFacets();
        if (required.isEmpty()) return false;
        return !currentFacets.containsAll(required);
    }

    /**
     * Erased helper — runtime types of the check's detail and the report's
     * detail are guaranteed identical by construction inside the plugin.
     * The cast is safe; suppress the static warning.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runOne(HealthCheck<? extends PluginEntity> check) {
        try {
            ProbeContext ctx = buildContext();
            String target = check.target(ctx);
            if (target == null || target.isBlank()) {
                LOG.debugf("probe %s skipped — no target", check.id());
                return;
            }
            HealthCheck raw = check;                          // erase the wildcard
            HealthReport report = raw.probe(ctx);             // typed for the plugin, raw here
            Observation obs = Observation.of(
                    cfg.clusterName(),
                    resolveClusterType(),
                    target,
                    cfg.nodeName(),
                    raw, report);
            publisher.publish(obs);
        } catch (Exception e) {
            LOG.warnf(e, "probe %s threw — skipping tick", check.id());
        }
    }

    private ClusterType resolveClusterType() {
        return ClusterType.parse(cfg.clusterType()).orElse(ClusterType.GENERIC);
    }

    private ProbeContext buildContext() {
        return new ProbeContext(
                cfg.clusterName(),
                cfg.nodeName(),
                Duration.ofSeconds(4),
                fabric);
    }
}
