package com.tb.nw.core.domain.api;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Plan.PlanStep;
import com.tb.nw.spi.api.Verdict;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Composes one ordered group failover plan from member sub-plans. Walks the
 * group's members in promote order, asks each for its activation sub-plan, and
 * concatenates the steps under one contiguous index. Single service or five,
 * the walk is identical — a one-member group yields exactly that member's plan.
 *
 * <p>Group-level fence (self) is the dying node's own behaviour, not a dispatched
 * step; group VIP becomes a leading step once the nw-vip member ships. Both are
 * additive here — a member that contributes them is simply ordered first.</p>
 */
@ApplicationScoped
public class GroupPlanGenerator {

    public Plan<CommandEvent> generate(FailoverGroup group, Verdict verdict,
                                       long failoverEpoch, Instant createdAt,
                                       MemberPlanSource memberPlans) {
        List<PlanStep<CommandEvent>> steps = new ArrayList<>();
        int index = 0;
        for (Member member : group.orderedMembers()) {
            Optional<Plan<? extends CommandEvent>> sub = memberPlans.planFor(member, verdict, failoverEpoch);
            if (sub.isEmpty()) continue;
            for (PlanStep<? extends CommandEvent> step : sub.get().steps()) {
                steps.add(new PlanStep<>(index++, step.kind(),
                        member.type() + ": " + step.description(),
                        step.command(), step.timeoutMillis(), step.maxRetries()));
            }
        }
        return new Plan<>(group.id() + "-failover", group.id(), failoverEpoch, createdAt, steps,
                Map.of("domain", group.id(), "verdict", verdict.name(),
                        "members", group.orderedMembers().stream().map(Member::type).toList()));
    }
}
