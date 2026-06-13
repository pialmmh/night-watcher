package com.tb.nw.core.domain.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailoverGroupTest {

    private static Member m(String type, int order, String... deps) {
        return new Member(type, "nw-" + type, "active-standby", order, List.of(deps));
    }

    private static FailoverGroup group(String id, List<Member> members) {
        return new FailoverGroup(id, members, Optional.empty(), FencePolicy.SELF,
                "member-odown:mysql", "node-a", "node-b");
    }

    @Test void singleServiceIsAOneMemberGroup() {
        FailoverGroup g = group("mysql-only", List.of(m("mysql", 1)));
        assertTrue(g.isSingleMember());
        assertEquals(1, g.orderedMembers().size());
        assertEquals("mysql", g.orderedMembers().get(0).type());
    }

    @Test void membersComeBackInPromoteOrder() {
        FailoverGroup g = group("btcl-sms", List.of(
                m("freeswitch", 5, "mysql", "sigtran"), m("mysql", 2),
                m("sigtran", 4, "mysql", "redis"), m("redis", 3)));
        assertEquals(List.of("mysql", "redis", "sigtran", "freeswitch"),
                g.orderedMembers().stream().map(Member::type).toList());
        assertFalse(g.isSingleMember());
    }

    @Test void emptyGroupRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> group("x", List.of()));
    }

    @Test void dependencyBeforeDependentIsValid() {
        group("ok", List.of(m("sigtran", 4, "mysql"), m("mysql", 2))).validateOrdering();
    }

    @Test void dependencyAfterDependentRejected() {
        FailoverGroup bad = group("bad", List.of(m("mysql", 2, "sigtran"), m("sigtran", 5)));
        assertThrows(IllegalArgumentException.class, bad::validateOrdering);
    }

    @Test void unknownDependencyRejected() {
        FailoverGroup g = group("z", List.of(m("mysql", 1, "ghost")));
        assertThrows(IllegalArgumentException.class, g::validateOrdering);
    }
}
