package com.tb.nw.core;

import com.tb.nw.spi.ClusterType;
import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.ProbeContext;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;

/**
 * Tick-driven runner. Discovers every CDI-registered HealthCheck and invokes
 * it on schedule. Converts each HealthReport into an Observation and hands
 * it to the ObservationPublisher.
 *
 * For now this is a flat "all checks every tick" model. The Activation engine
 * (next iteration) will filter by predicates and per-investigator schedule.
 */
@Startup
@ApplicationScoped
public class InvestigatorRunner {

    private static final Logger LOG = Logger.getLogger(InvestigatorRunner.class);

    @Inject AgentConfig cfg;
    @Inject Instance<HealthCheck> checks;
    @Inject ObservationPublisher publisher;
    @Inject FacetPublisher facets;

    @Scheduled(every = "5s", delayed = "6s")
    public void tick() {
        var currentFacets = facets.snapshot();
        for (HealthCheck check : checks) {
            if (notApplicable(check, currentFacets)) continue;
            runOne(check);
        }
    }

    private boolean notApplicable(HealthCheck check, java.util.Set<String> currentFacets) {
        var required = check.requiredFacets();
        if (required.isEmpty()) return false;
        return !currentFacets.containsAll(required);
    }

    private void runOne(HealthCheck check) {
        try {
            HealthReport report = check.probe(buildContext());
            Observation obs = Observation.of(
                    cfg.clusterName(),
                    resolveClusterType(),
                    cfg.nodeName(),
                    cfg.nodeName(),
                    check, report);
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
                Map.of());
    }
}
