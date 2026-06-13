package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.dependencies.MysqlConfig;
import com.tb.nw.plugins.mysql.publishes.MySqlRemoteHealth;
import com.tb.nw.spi.api.CandidateSelector;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.NodeId;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.ObservationView;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.SelectionContext;
import com.tb.nw.spi.api.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Evidence, not guesswork. Filters each candidate's freshest LOCAL_SELF
 * observation: stale (older than {@value #FRESHNESS_SECONDS} s) is no
 * evidence, DEAD is out, replica threads not running is out. Ranks by least
 * lag (unknown lag last), ties by node name — deterministic, so two brains
 * looking at the same evidence name the same node. Empty pool ⇒ refuses.
 */
@ApplicationScoped
public class MysqlCandidateSelector implements CandidateSelector<MySqlRemoteHealth> {

    private static final Logger LOG = Logger.getLogger(MysqlCandidateSelector.class);
    private static final long FRESHNESS_SECONDS = 30;

    @Inject MysqlPluginDescriptor descriptor;
    @Inject MysqlConfig cfg;

    /** Role 2 lag ceiling; overridden from config at startup. Default keeps unit tests config-free. */
    long maxPromotionLagSec = 10;

    @jakarta.annotation.PostConstruct
    void init() { maxPromotionLagSec = cfg.maxPromotionLagSec(); }

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override
    public Optional<NodeId> select(List<ObservationView<MySqlRemoteHealth>> candidates, SelectionContext ctx) {
        Instant freshnessFloor = Instant.now().minus(Duration.ofSeconds(FRESHNESS_SECONDS));

        List<Scored> pool = candidates.stream()
                .map(view -> score(view, freshnessFloor))
                .flatMap(Optional::stream)
                .sorted(Comparator
                        .comparingLong(Scored::lagSeconds)
                        .thenComparing(Scored::node))
                .toList();

        if (pool.isEmpty()) {
            LOG.warn("candidate selection refused: no slave with fresh, alive, replicating evidence");
            return Optional.empty();
        }
        Scored winner = pool.get(0);
        LOG.infof("promotion candidate: %s (lag=%ds, pool=%d)", winner.node(), winner.lagSeconds(), pool.size());
        return Optional.of(NodeId.of(winner.node()));
    }

    private record Scored(String node, long lagSeconds) {}

    /** One candidate's verdict from its freshest LOCAL_SELF observation. */
    private Optional<Scored> score(ObservationView<MySqlRemoteHealth> view, Instant freshnessFloor) {
        Optional<Observation<MySqlRemoteHealth>> latestLocal = view.observations().stream()
                .filter(o -> o.vantage() == Vantage.LOCAL_SELF)
                .max(Comparator.comparing(Observation::freshness));
        if (latestLocal.isEmpty()) return Optional.empty();

        Observation<MySqlRemoteHealth> obs = latestLocal.get();
        if (obs.freshness().isBefore(freshnessFloor)) return Optional.empty();   // stale = no evidence
        if (obs.state() == HealthState.DEAD || obs.state() == HealthState.UNKNOWN) return Optional.empty();

        MySqlRemoteHealth health = obs.event();
        // Role 2 gate — the slave's own "I can assume master" assertion: replica
        // threads running AND caught up within the lag window. Promoting a lagging
        // slave loses writes, so a slave that lacks in binlog is no candidate.
        if (!health.promotable(maxPromotionLagSec)) return Optional.empty();

        return Optional.of(new Scored(view.target(), health.secondsBehindMaster()));
    }
}
