package com.tb.nw.core.lifecycle.internal;

import com.tb.nw.core.dependencies.AgentConfig;
import com.tb.nw.fabric.api.Fabric;

/** Same-package factory so tests elsewhere can build lifecycle beans. */
public final class TestLifecycle {
    private TestLifecycle() {}

    public static AgentLease lease(AgentConfig cfg, Fabric fabric) {
        AgentLease l = new AgentLease();
        l.cfg = cfg;
        l.fabric = fabric;
        return l;
    }
}
