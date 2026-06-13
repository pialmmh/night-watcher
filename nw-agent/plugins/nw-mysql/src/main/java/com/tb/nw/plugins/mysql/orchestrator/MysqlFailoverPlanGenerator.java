package com.tb.nw.plugins.mysql.orchestrator;

import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.events.MySqlCommandEvent;
import com.tb.nw.plugins.mysql.events.MySqlFenceMasterCommand;
import com.tb.nw.plugins.mysql.events.MySqlPromoteSlaveCommand;
import com.tb.nw.plugins.mysql.events.MySqlRemoteHealth;
import com.tb.nw.spi.api.ClusterView;
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
 * Builds the MySQL failover plan the Coordinator walks. Convention (shared
 * with the Coordinator): <b>the last step is the promotion</b>; every step
 * before it is fencing.
 *
 * <p>Steps produced:</p>
 * <ol>
 *   <li>fence-master ACTION_CALL targeting the roles-board master — omitted
 *       when no master is on the board (the durable quarantine still
 *       happens Coordinator-side per fence step's target).</li>
 *   <li>promote-slave ACTION_CALL targeting the Selector's pick. No
 *       candidate ⇒ empty plan — the Coordinator fails the failover loudly
 *       rather than promote a guess.</li>
 * </ol>
 */
@ApplicationScoped
public class MysqlFailoverPlanGenerator implements FailoverPlanGenerator<MySqlRemoteHealth, MySqlCommandEvent> {

    private static final Logger LOG = Logger.getLogger(MysqlFailoverPlanGenerator.class);

    @Inject MysqlPluginDescriptor descriptor;
    @Inject MysqlCandidateSelector selector;

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override
    public Plan<MySqlCommandEvent> generate(Verdict verdict, ClusterView<MySqlRemoteHealth> view) {
        Optional<String> master = masterOnBoard(view);
        Optional<NodeId> candidate = pickCandidate(view);

        if (candidate.isEmpty()) {
            LOG.warn("no promotion candidate — returning empty plan (coordinator fails loudly)");
            return emptyPlan(view, verdict);
        }

        List<Plan.PlanStep<MySqlCommandEvent>> steps = new ArrayList<>();
        master.ifPresentOrElse(
                node -> steps.add(fenceStep(steps.size(), view.failoverEpoch(), node)),
                () -> LOG.info("no master on the roles board — plan has no fence step"));
        steps.add(promoteStep(steps.size(), view.failoverEpoch(), candidate.get().value()));

        return new Plan<>(
                NwEvent.newEventId(),
                view.cluster(),
                view.failoverEpoch(),
                Instant.now(),
                steps,
                Map.of("verdict", verdict.name(),
                       "master", master.orElse("<none>"),
                       "candidate", candidate.get().value()));
    }

    // ── steps ──

    private Plan.PlanStep<MySqlCommandEvent> fenceStep(int index, long epoch, String masterNode) {
        return new Plan.PlanStep<>(index, "ACTION_CALL",
                "fence old master " + masterNode,
                MySqlFenceMasterCommand.of(epoch, masterNode, false),
                8000, 1);
    }

    private Plan.PlanStep<MySqlCommandEvent> promoteStep(int index, long epoch, String candidate) {
        return new Plan.PlanStep<>(index, "ACTION_CALL",
                "promote " + candidate + " to master",
                MySqlPromoteSlaveCommand.of(epoch, candidate, null),   // GTID floor: Extras — later cut
                8000, 1);
    }

    // ── evidence assembly ──

    private Optional<String> masterOnBoard(ClusterView<MySqlRemoteHealth> view) {
        return view.rolesByNode().entrySet().stream()
                .filter(e -> e.getValue().contains("master"))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Slave pool = roles-board slaves, each with its observations grouped into a view. */
    private Optional<NodeId> pickCandidate(ClusterView<MySqlRemoteHealth> view) {
        Map<String, List<Observation<MySqlRemoteHealth>>> byTarget = new HashMap<>();
        for (Observation<MySqlRemoteHealth> o : view.recentObservations()) {
            byTarget.computeIfAbsent(o.target(), k -> new ArrayList<>()).add(o);
        }
        List<ObservationView<MySqlRemoteHealth>> slaveViews = view.rolesByNode().entrySet().stream()
                .filter(e -> e.getValue().contains("slave"))
                .map(e -> new ObservationView<>(e.getKey(), byTarget.getOrDefault(e.getKey(), List.of())))
                .toList();

        SelectionContext ctx = new SelectionContext(
                view.cluster(), view.clusterType(), view.rolesByNode(), List.of());
        return selector.select(slaveViews, ctx);
    }

    private Plan<MySqlCommandEvent> emptyPlan(ClusterView<MySqlRemoteHealth> view, Verdict verdict) {
        return new Plan<>(NwEvent.newEventId(), view.cluster(), view.failoverEpoch(),
                Instant.now(), List.of(), Map.of("verdict", verdict.name(), "reason", "no-candidate"));
    }
}
