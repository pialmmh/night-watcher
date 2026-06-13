package com.tb.nw.plugins.mock.orchestrator;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.plugins.mock.events.MockFenceCommand;
import com.tb.nw.plugins.mock.events.MockHealth;
import com.tb.nw.plugins.mock.events.MockPromoteCommand;
import com.tb.nw.spi.api.ClusterView;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.FailoverPlanGenerator;
import com.tb.nw.spi.api.NodeId;
import com.tb.nw.spi.api.NwEvent;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.ObservationView;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.SelectionContext;
import com.tb.nw.spi.api.Verdict;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mock failover plan — same convention as the MySQL generator: the LAST
 * step is the promotion; everything before it is fencing. No candidate ⇒
 * empty plan ⇒ the coordinator fails loudly.
 */
@ApplicationScoped
public class MockFailoverPlanGenerator implements FailoverPlanGenerator<MockHealth, CommandEvent> {

    private static final Logger LOG = Logger.getLogger(MockFailoverPlanGenerator.class);

    @Inject MockPluginDescriptor descriptor;
    @Inject MockCandidateSelector selector;

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override
    public Plan<CommandEvent> generate(Verdict verdict, ClusterView<MockHealth> view) {
        Optional<String> master = masterOnBoard(view);
        Optional<NodeId> candidate = pickCandidate(view);

        if (candidate.isEmpty()) {
            LOG.warn("no promotion candidate — returning empty plan (coordinator fails loudly)");
            return new Plan<>(NwEvent.newEventId(), view.cluster(), view.failoverEpoch(),
                    Instant.now(), List.of(),
                    Map.of("verdict", verdict.name(), "reason", "no-candidate"));
        }

        List<Plan.PlanStep<CommandEvent>> steps = new ArrayList<>();
        master.ifPresentOrElse(
                node -> steps.add(new Plan.PlanStep<>(steps.size(), "ACTION_CALL",
                        "fence old master " + node,
                        MockFenceCommand.of(view.failoverEpoch(), node), 8000, 1)),
                () -> LOG.info("no master on the roles board — plan has no fence step"));
        steps.add(new Plan.PlanStep<>(steps.size(), "ACTION_CALL",
                "promote " + candidate.get() + " to master",
                MockPromoteCommand.of(view.failoverEpoch(), candidate.get().value()), 8000, 1));

        return new Plan<>(NwEvent.newEventId(), view.cluster(), view.failoverEpoch(),
                Instant.now(), steps,
                Map.of("verdict", verdict.name(),
                       "master", master.orElse("<none>"),
                       "candidate", candidate.get().value()));
    }

    private Optional<String> masterOnBoard(ClusterView<MockHealth> view) {
        return view.rolesByNode().entrySet().stream()
                .filter(e -> e.getValue().contains("master"))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Slave pool = roles-board slaves, each with its observations grouped into a view. */
    private Optional<NodeId> pickCandidate(ClusterView<MockHealth> view) {
        Map<String, List<Observation<MockHealth>>> byTarget = new HashMap<>();
        for (Observation<MockHealth> o : view.recentObservations()) {
            byTarget.computeIfAbsent(o.target(), k -> new ArrayList<>()).add(o);
        }
        List<ObservationView<MockHealth>> slaveViews = view.rolesByNode().entrySet().stream()
                .filter(e -> e.getValue().contains("slave"))
                .map(e -> new ObservationView<>(e.getKey(), byTarget.getOrDefault(e.getKey(), List.of())))
                .toList();

        SelectionContext ctx = new SelectionContext(
                view.cluster(), view.clusterType(), view.rolesByNode(), List.of());
        return selector.select(slaveViews, ctx);
    }
}
