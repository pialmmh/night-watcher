package com.tb.nw.core.boards.api;

import com.tb.nw.core.lifecycle.internal.TestLifecycle;
import com.tb.nw.core.observe.publishes.FacetPublisher;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.testkit.Fakes;

import java.util.Set;

/** Same-package factory so tests elsewhere can build real boards over a FakeFabric. */
public final class TestBoards {

    private TestBoards() {}

    public static FailoverGate gate(Fabric fabric, String node, String cluster) {
        var cfg = Fakes.agentConfig(node, cluster);
        FailoverGate g = new FailoverGate();
        g.cfg = cfg;
        g.fabric = fabric;
        g.lease = TestLifecycle.lease(cfg, fabric);
        return g;
    }

    public static RoleStore roleStore(Fabric fabric, String node, String cluster) {
        RoleStore s = new RoleStore();
        s.cfg = Fakes.agentConfig(node, cluster);
        s.fabric = fabric;
        s.facets = new FacetPublisher() {
            @Override public Set<String> snapshot() { return Set.of(); }
        };
        return s;
    }
}
