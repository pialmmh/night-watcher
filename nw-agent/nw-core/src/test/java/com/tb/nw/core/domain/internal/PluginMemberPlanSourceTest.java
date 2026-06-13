package com.tb.nw.core.domain.internal;

import com.tb.nw.core.boards.api.TestBoards;
import com.tb.nw.core.cache.api.ObservationCache;
import com.tb.nw.core.domain.api.Member;
import com.tb.nw.core.domain.internal.GroupRuntimeFakes.FakeGenerator;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.FailoverPlanGenerator;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Verdict;
import com.tb.nw.testkit.FakeFabric;
import com.tb.nw.testkit.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.tb.nw.core.domain.internal.GroupRuntimeFakes.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMemberPlanSourceTest {

    private PluginMemberPlanSource source;

    @BeforeEach void setUp() {
        source = new PluginMemberPlanSource();
        source.agentCfg = Fakes.agentConfig("node-a", "c1");
        source.roles = TestBoards.roleStore(new FakeFabric(), "node-a", "c1");
        source.cache = new ObservationCache();
    }

    private static Member mysql() {
        return new Member("mysql", "nw-mysql", "master-slave", 1, List.of());
    }

    @Test void matchingPluginGetsItsSubPlanWithGroupEpoch() {
        FakeGenerator gen = new FakeGenerator("nw-mysql",
                List.of(step("promote-slave", "promote slave", "node-b", 0)));
        source.generators = Fakes.instanceOf(List.<FailoverPlanGenerator<?, ?>>of(gen));

        Optional<Plan<? extends CommandEvent>> plan = source.planFor(mysql(), Verdict.ODOWN, 42);

        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().steps().size());
        assertEquals(42, gen.sawEpoch);                       // the group's epoch reached the member's view
    }

    @Test void noMatchingPluginYieldsEmpty() {
        FakeGenerator other = new FakeGenerator("nw-redis", List.of());
        source.generators = Fakes.instanceOf(List.<FailoverPlanGenerator<?, ?>>of(other));

        assertTrue(source.planFor(mysql(), Verdict.ODOWN, 1).isEmpty());
    }
}
