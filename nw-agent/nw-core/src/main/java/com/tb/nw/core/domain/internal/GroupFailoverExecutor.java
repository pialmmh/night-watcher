package com.tb.nw.core.domain.internal;

import com.tb.nw.core.boards.api.FailoverGate;
import com.tb.nw.core.boards.api.RoleStore;
import com.tb.nw.core.domain.api.CommandSink;
import com.tb.nw.core.domain.api.FailoverGroup;
import com.tb.nw.core.domain.api.GroupFailoverOutcome;
import com.tb.nw.core.domain.api.GroupPlanGenerator;
import com.tb.nw.core.domain.api.MemberPlanSource;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Plan.PlanStep;
import com.tb.nw.spi.api.Verdict;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Executes one domain failover, uniform over single- and multi-member groups:
 * take the gate, build the ordered group plan, quarantine the old active node,
 * walk the steps (best-effort to the dying node, must-succeed to the new one),
 * mark the new active node, release the gate. Plain class — constructed with its
 * collaborators so the whole sequence is unit-testable without CDI.
 */
public class GroupFailoverExecutor {

    private static final Logger LOG = Logger.getLogger(GroupFailoverExecutor.class);

    private final FailoverGate gate;
    private final RoleStore roles;
    private final GroupPlanGenerator planGenerator;
    private final MemberPlanSource memberPlans;
    private final CommandSink sink;
    private final Clock clock;

    public GroupFailoverExecutor(FailoverGate gate, RoleStore roles, GroupPlanGenerator planGenerator,
                                 MemberPlanSource memberPlans, CommandSink sink, Clock clock) {
        this.gate = gate;
        this.roles = roles;
        this.planGenerator = planGenerator;
        this.memberPlans = memberPlans;
        this.sink = sink;
        this.clock = clock;
    }

    public GroupFailoverOutcome runFailover(FailoverGroup group, Verdict verdict) {
        if (group.activeNode().equals(group.standbyNode()))
            return GroupFailoverOutcome.failed("active and standby are the same node");
        if (!gate.tryAcquire()) {
            LOG.infof("another agent leads failover for %s — standing down", group.id());
            return GroupFailoverOutcome.stoodDown();
        }
        try {
            return promoteUnderGate(group, verdict);
        } finally {
            gate.release();
        }
    }

    private GroupFailoverOutcome promoteUnderGate(FailoverGroup group, Verdict verdict) {
        long epoch = gate.bumpEpoch();
        Plan<CommandEvent> plan = planGenerator.generate(group, verdict, epoch, Instant.now(clock), memberPlans);
        if (plan.steps().isEmpty())
            return GroupFailoverOutcome.failed("empty plan — no member contributed steps");

        roles.put(group.activeNode(), RoleStore.QUARANTINED);     // the durable fence
        for (PlanStep<CommandEvent> step : plan.steps()) {
            String reject = runStep(group, step);
            if (reject != null) return GroupFailoverOutcome.failed(reject);
        }
        roles.put(group.standbyNode(), RoleStore.MASTER);
        LOG.infof("domain %s failed over — %s is now active", group.id(), group.standbyNode());
        return GroupFailoverOutcome.done(group.standbyNode());
    }

    /** @return null when the step is satisfied; a reason string when a must-succeed step fails. */
    private String runStep(FailoverGroup group, PlanStep<CommandEvent> step) {
        CommandEvent cmd = step.command();
        if (cmd == null) return null;                              // ETCD_TXN — nothing to dispatch
        boolean bestEffort = cmd.targetNode().equals(group.activeNode());
        Duration budget = Duration.ofMillis(Math.max(1, step.timeoutMillis()));
        try {
            CommandResultEvent result = sink.send(cmd, budget);
            if (!bestEffort && (result == null || !result.ok()))
                return "step failed on " + cmd.targetNode() + ": " + step.description()
                        + (result != null ? " (" + result.reason() + ")" : "");
            return null;
        } catch (Exception e) {
            if (bestEffort) {
                LOG.warnf("best-effort step undeliverable (%s) — quarantine is the fence: %s",
                        e.getMessage(), step.description());
                return null;
            }
            return "step undeliverable on " + cmd.targetNode() + ": " + e.getMessage();
        }
    }
}
