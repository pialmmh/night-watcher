package com.tb.nw.core.domain.api;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Plan.PlanStep;
import com.tb.nw.spi.api.Verdict;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupPlanGeneratorTest {

    private final GroupPlanGenerator gen = new GroupPlanGenerator();

    private record FakeCommand(String pluginId, String pluginVersion, String eventId,
                               Instant timestamp, String commandKind, String targetNode,
                               long failoverEpoch) implements CommandEvent {}

    private static PlanStep<CommandEvent> action(String kind, String desc, String node) {
        return new PlanStep<>(0, "ACTION_CALL", desc,
                new FakeCommand("nw-" + kind, "1", "e", Instant.EPOCH, kind, node, 7), 1000, 0);
    }

    private static Plan<CommandEvent> sub(List<PlanStep<CommandEvent>> steps) {
        return new Plan<>("p", "c", 7, Instant.EPOCH, steps, Map.of());
    }

    private static Member m(String type, int order) {
        return new Member(type, "nw-" + type, "active-standby", order, List.of());
    }

    private static FailoverGroup group(Member... members) {
        return new FailoverGroup("btcl-sms", List.of(members), Optional.empty(),
                FencePolicy.SELF, "member-odown:mysql", "node-a", "node-b");
    }

    @Test void oneMemberPlanIsJustThatMembersSubPlan() {
        MemberPlanSource src = (mem, v) -> Optional.of(sub(List.of(action("promote", "promote slave", "node-b"))));
        Plan<CommandEvent> plan = gen.generate(group(m("mysql", 1)), Verdict.ODOWN, 7, Instant.EPOCH, src);
        assertEquals(1, plan.steps().size());
        assertEquals(0, plan.steps().get(0).index());
        assertEquals("mysql: promote slave", plan.steps().get(0).description());
    }

    @Test void multiMemberConcatenatesInPromoteOrderWithContiguousIndex() {
        FailoverGroup g = group(m("sigtran", 4), m("mysql", 2), m("redis", 3));
        MemberPlanSource src = (mem, v) -> switch (mem.type()) {
            case "mysql" -> Optional.of(sub(List.of(
                    action("fence", "fence master", "node-a"), action("promote", "promote slave", "node-b"))));
            case "redis" -> Optional.of(sub(List.of(action("promote", "REPLICAOF NO ONE", "node-b"))));
            case "sigtran" -> Optional.of(sub(List.of(action("activate", "bind point codes", "node-b"))));
            default -> Optional.empty();
        };
        Plan<CommandEvent> plan = gen.generate(g, Verdict.ODOWN, 7, Instant.EPOCH, src);
        assertEquals(List.of(0, 1, 2, 3), plan.steps().stream().map(PlanStep::index).toList());
        assertEquals(List.of("mysql: fence master", "mysql: promote slave",
                        "redis: REPLICAOF NO ONE", "sigtran: bind point codes"),
                plan.steps().stream().map(PlanStep::description).toList());
    }

    @Test void memberWithNoSubPlanIsSkipped() {
        MemberPlanSource src = (mem, v) -> mem.type().equals("mysql")
                ? Optional.of(sub(List.of(action("promote", "promote slave", "node-b")))) : Optional.empty();
        Plan<CommandEvent> plan = gen.generate(group(m("mysql", 1), m("ghost", 2)),
                Verdict.ODOWN, 7, Instant.EPOCH, src);
        assertEquals(1, plan.steps().size());
    }

    @Test void planCarriesDomainIdAndEpoch() {
        MemberPlanSource src = (mem, v) -> Optional.of(sub(List.of(action("promote", "x", "node-b"))));
        Plan<CommandEvent> plan = gen.generate(group(m("mysql", 1)), Verdict.ODOWN, 42, Instant.EPOCH, src);
        assertEquals("btcl-sms-failover", plan.id());
        assertEquals("btcl-sms", plan.cluster());
        assertEquals(42, plan.failoverEpoch());
    }
}
