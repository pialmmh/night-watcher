package com.tb.nw.core.domain.internal;

import com.tb.nw.core.boards.api.FailoverGate;
import com.tb.nw.core.boards.api.RoleStore;
import com.tb.nw.core.boards.api.TestBoards;
import com.tb.nw.core.domain.api.FailoverGroup;
import com.tb.nw.core.domain.api.FencePolicy;
import com.tb.nw.core.domain.api.GroupFailoverOutcome;
import com.tb.nw.core.domain.api.GroupPlanGenerator;
import com.tb.nw.core.domain.api.Member;
import com.tb.nw.core.domain.api.MemberPlanSource;
import com.tb.nw.core.domain.internal.GroupRuntimeFakes.RecordingSink;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.Verdict;
import com.tb.nw.testkit.FakeFabric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.tb.nw.core.domain.internal.GroupRuntimeFakes.step;
import static com.tb.nw.core.domain.internal.GroupRuntimeFakes.subPlan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupFailoverExecutorTest {

    private FakeFabric fabric;
    private FailoverGate gate;
    private RoleStore roles;
    private final GroupPlanGenerator planGen = new GroupPlanGenerator();
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @BeforeEach void setUp() {
        fabric = new FakeFabric();
        gate = TestBoards.gate(fabric, "node-b", "c1");      // this agent = the survivor
        roles = TestBoards.roleStore(fabric, "node-b", "c1");
    }

    private static FailoverGroup mysqlOnly() {
        return new FailoverGroup("mysql-only",
                List.of(new Member("mysql", "nw-mysql", "master-slave", 1, List.of())),
                Optional.empty(), FencePolicy.SELF, "member-odown:mysql", "node-a", "node-b");
    }

    /** mysql sub-plan: fence old master (node-a, best-effort) then promote slave (node-b, must-succeed). */
    private static MemberPlanSource mysqlSubPlan() {
        return (member, verdict, epoch) -> Optional.of(subPlan(epoch,
                step("fence-master", "fence master", "node-a", epoch),
                step("promote-slave", "promote slave", "node-b", epoch)));
    }

    private GroupFailoverExecutor exec(MemberPlanSource plans, RecordingSink sink) {
        return new GroupFailoverExecutor(gate, roles, planGen, plans, sink, clock);
    }

    @Test void oneMemberDomainPromotesEndToEnd() {
        RecordingSink sink = RecordingSink.allOk();
        GroupFailoverOutcome out = exec(mysqlSubPlan(), sink).runFailover(mysqlOnly(), Verdict.ODOWN);

        assertTrue(out.ok());
        assertEquals("node-b", out.newActiveNode());
        assertEquals(List.of("fence-master", "promote-slave"),
                sink.sent.stream().map(CommandEvent::commandKind).toList());
        assertTrue(roles.rolesOf("node-a").contains(RoleStore.QUARANTINED));
        assertTrue(roles.rolesOf("node-b").contains(RoleStore.MASTER));
    }

    @Test void standsDownWhenGateHeld() {
        FailoverGate otherAgent = TestBoards.gate(fabric, "other-agent", "c1");
        assertTrue(otherAgent.tryAcquire());                  // a DIFFERENT agent already leads
        RecordingSink sink = RecordingSink.allOk();
        GroupFailoverOutcome out = exec(mysqlSubPlan(), sink).runFailover(mysqlOnly(), Verdict.ODOWN);

        assertEquals(GroupFailoverOutcome.Status.STOOD_DOWN, out.status());
        assertTrue(sink.sent.isEmpty());
    }

    @Test void failsWhenPromoteRefused() {
        RecordingSink sink = new RecordingSink(c -> !c.commandKind().equals("promote-slave"), c -> false);
        GroupFailoverOutcome out = exec(mysqlSubPlan(), sink).runFailover(mysqlOnly(), Verdict.ODOWN);

        assertEquals(GroupFailoverOutcome.Status.FAILED, out.status());
        assertFalse(roles.rolesOf("node-b").contains(RoleStore.MASTER));
    }

    @Test void fenceUndeliverableToDeadMasterStillPromotes() {
        // the fence step targets the dead master (node-a) and throws; promote (node-b) succeeds
        RecordingSink sink = new RecordingSink(c -> true, c -> c.targetNode().equals("node-a"));
        GroupFailoverOutcome out = exec(mysqlSubPlan(), sink).runFailover(mysqlOnly(), Verdict.ODOWN);

        assertTrue(out.ok());
        assertTrue(roles.rolesOf("node-b").contains(RoleStore.MASTER));
    }

    @Test void emptyPlanFails() {
        MemberPlanSource noSteps = (member, verdict, epoch) -> Optional.empty();
        GroupFailoverOutcome out = exec(noSteps, RecordingSink.allOk()).runFailover(mysqlOnly(), Verdict.ODOWN);
        assertEquals(GroupFailoverOutcome.Status.FAILED, out.status());
    }
}
