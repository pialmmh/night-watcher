package com.tb.nw.core.domain.api;

import com.tb.nw.spi.api.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupTriggerTest {

    private final GroupTrigger trigger = new GroupTrigger();

    private static Member m(String type, int order) {
        return new Member(type, "nw-" + type, "active-standby", order, List.of());
    }

    private static FailoverGroup group(String triggerCondition, Member... members) {
        return new FailoverGroup("d", List.of(members), Optional.empty(),
                FencePolicy.SELF, triggerCondition, "node-a", "node-b");
    }

    private static Function<Member, Optional<Verdict>> verdicts(Map<String, Verdict> byType) {
        return member -> Optional.ofNullable(byType.get(member.type()));
    }

    @Test void firesWhenTriggerMemberIsOdown() {
        FailoverGroup g = group("member-odown:mysql", m("mysql", 2), m("redis", 3));
        assertTrue(trigger.isDomainDown(g, verdicts(Map.of("mysql", Verdict.ODOWN))));
    }

    @Test void doesNotFireOnSdownOrUp() {
        FailoverGroup g = group("member-odown:mysql", m("mysql", 1));
        assertFalse(trigger.isDomainDown(g, verdicts(Map.of("mysql", Verdict.SDOWN))));
        assertFalse(trigger.isDomainDown(g, verdicts(Map.of("mysql", Verdict.UP))));
    }

    @Test void onlyTheTriggerMemberMatters() {
        FailoverGroup g = group("member-odown:mysql", m("mysql", 2), m("sigtran", 4));
        // sigtran ODOWN but the trigger watches mysql, which is UP -> no group failover
        assertFalse(trigger.isDomainDown(g,
                verdicts(Map.of("mysql", Verdict.UP, "sigtran", Verdict.ODOWN))));
    }

    @Test void noVerdictMeansNotDown() {
        FailoverGroup g = group("member-odown:mysql", m("mysql", 1));
        assertFalse(trigger.isDomainDown(g, verdicts(Map.of())));
    }

    @Test void unknownTriggerRejected() {
        FailoverGroup g = group("on-tuesdays", m("mysql", 1));
        assertThrows(IllegalArgumentException.class,
                () -> trigger.isDomainDown(g, verdicts(Map.of())));
    }
}
