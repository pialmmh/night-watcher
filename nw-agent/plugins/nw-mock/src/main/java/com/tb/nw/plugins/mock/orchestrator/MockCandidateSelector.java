package com.tb.nw.plugins.mock.orchestrator;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.plugins.mock.events.MockHealth;
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
 * Picks the standby to promote, from each candidate's own fresh LOCAL_SELF
 * confession: must not be DEAD, must not be fenced, must say role=slave.
 * Rank FAST before DEGRADED, tie by node name. Empty pool → refuse — the
 * coordinator fails loudly rather than promote a guess.
 */
@ApplicationScoped
public class MockCandidateSelector implements CandidateSelector<MockHealth> {

    private static final Logger LOG = Logger.getLogger(MockCandidateSelector.class);
    private static final long FRESHNESS_SECONDS = 30;

    @Inject MockPluginDescriptor descriptor;

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override
    public Optional<NodeId> select(List<ObservationView<MockHealth>> candidates, SelectionContext ctx) {
        Instant floor = Instant.now().minus(Duration.ofSeconds(FRESHNESS_SECONDS));
        return candidates.stream()
                .flatMap(view -> freshestSelfConfession(view, floor).stream())
                .filter(this::eligible)
                .sorted(Comparator
                        .comparingInt((Observation<MockHealth> o) -> o.state() == HealthState.FAST ? 0 : 1)
                        .thenComparing(Observation::target))
                .map(o -> NodeId.of(o.target()))
                .findFirst();
    }

    private Optional<Observation<MockHealth>> freshestSelfConfession(
            ObservationView<MockHealth> view, Instant floor) {
        return view.observations().stream()
                .filter(o -> o.vantage() == Vantage.LOCAL_SELF)
                .filter(o -> o.freshness().isAfter(floor))
                .max(Comparator.comparing(Observation::freshness));
    }

    private boolean eligible(Observation<MockHealth> o) {
        MockHealth h = o.event();
        boolean ok = o.state() != HealthState.DEAD
                && o.state() != HealthState.UNKNOWN
                && !h.fenced()
                && "slave".equals(h.role());
        if (!ok) LOG.debugf("candidate %s rejected: state=%s fenced=%s role=%s",
                o.target(), o.state(), h.fenced(), h.role());
        return ok;
    }
}
