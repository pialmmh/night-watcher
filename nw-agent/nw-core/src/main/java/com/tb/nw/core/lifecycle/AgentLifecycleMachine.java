package com.tb.nw.core.lifecycle;

import com.tb.nw.core.FabricProducer;
import com.tb.nw.core.observe.InvestigatorRunner;

import com.tb.nw.core.AgentConfig;
import com.tb.nw.core.lifecycle.AgentState;
import com.tb.nw.core.observe.FacetPublisher;
import com.tb.nw.core.lifecycle.events.DrainRequested;
import com.tb.nw.core.lifecycle.events.FabricReachable;
import com.tb.nw.core.lifecycle.events.FabricUnreachable;
import com.tb.nw.core.lifecycle.events.FacetsDetected;
import com.tb.nw.core.lifecycle.events.InvestigatorsArmed;
import com.tb.nw.core.sm.StateMap;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.etcd.EtcdFabric;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import com.tb.nw.spi.HealthCheck;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level agent lifecycle daemon. The top of this class spells out the
 * named business phases of the agent's life:
 *
 * <pre>
 *   STARTING
 *      │
 *      ▼  (CDI brings Fabric / FacetPublisher / InvestigatorRunner up)
 *   CONNECTING_FABRIC ──FabricReachable──► DETECTING_FACETS
 *           │ timeout                              │
 *           ▼                                      ▼
 *        ERROR                            FacetsDetected
 *                                                  │
 *                                                  ▼
 *                                          ARMING_INVESTIGATORS
 *                                                  │
 *                                       InvestigatorsArmed
 *                                                  ▼
 *                                                ALIVE
 *                                                  │
 *                                          DrainRequested
 *                                                  ▼
 *                                              DRAINING
 *                                                  │
 *                                                  ▼
 *                                              STOPPED
 * </pre>
 *
 * The work itself is done by the CDI {@code @Startup} beans the agent
 * already runs ({@code FabricProducer}, {@code FacetPublisher},
 * {@code InvestigatorRunner}, etc.) — this machine names the phases and
 * publishes status, it does not duplicate their work. A 1-Hz heartbeat
 * checks fabric reachability + facet readiness and fires the appropriate
 * events.
 */
@Startup
@ApplicationScoped
public class AgentLifecycleMachine {

    private static final Logger LOG = Logger.getLogger(AgentLifecycleMachine.class);

    @Inject AgentConfig cfg;
    @Inject AgentState state;
    @Inject Fabric fabric;
    @Inject FacetPublisher facets;
    @Inject Instance<HealthCheck> healthChecks;

    private final AtomicReference<StateMap<AgentLifecycleMachine>> machine = new AtomicReference<>();

    @PostConstruct
    void init() {
        StateMap<AgentLifecycleMachine> sm = build();
        sm.start(this);
        machine.set(sm);
        LOG.info("AgentLifecycleMachine started in STARTING");
    }

    @PreDestroy
    void shutdown() {
        StateMap<AgentLifecycleMachine> sm = machine.getAndSet(null);
        if (sm != null) {
            sm.fire(new DrainRequested("preDestroy"));
            sm.close();
        }
    }

    /** Visible to /agent/info. */
    public String currentPhase() {
        StateMap<AgentLifecycleMachine> sm = machine.get();
        return sm == null ? "stopped" : sm.currentState();
    }

    /**
     * Drives lifecycle progression. Each tick checks the world and fires the
     * appropriate events; the SM ignores unmatched events for the current
     * state, so this is safe to call repeatedly.
     */
    @Scheduled(every = "1s", delayed = "1s")
    void heartbeat() {
        StateMap<AgentLifecycleMachine> sm = machine.get();
        if (sm == null || sm.isTerminal()) return;

        // Fabric probe — drives STARTING / CONNECTING_FABRIC → DETECTING_FACETS / ERROR.
        boolean fabricUp = isFabricReachable();
        if (fabricUp) {
            sm.fire(new FabricReachable());
        } else {
            sm.fire(new FabricUnreachable("ping failed"));
        }

        // Facet readiness — drives DETECTING_FACETS → ARMING_INVESTIGATORS.
        // An empty facet set is a valid detection result for hosts that
        // don't host any of the services we know about (e.g. a coordinator
        // or witness node) — fire regardless so the SM advances.
        sm.fire(new FacetsDetected(facets.snapshot()));

        // Investigator count — drives ARMING_INVESTIGATORS → ALIVE.
        int registered = countRegisteredChecks();
        if (registered > 0) {
            sm.fire(new InvestigatorsArmed(registered));
        }
    }

    // ── state machine entry — names the business phases at the top ──

    private StateMap<AgentLifecycleMachine> build() {
        return StateMap.<AgentLifecycleMachine>builder()
                .name("AgentLifecycleMachine")
                .auditSink(ev -> LOG.infof("AgentLifecycle: %s -> %s", ev.from(), ev.to()))
                .initialState("STARTING")

                .state("STARTING")
                    .interim()
                    .onEntry(self -> self.markJoining("STARTING"))
                    .on(FabricReachable.class, "DETECTING_FACETS")
                    .on(FabricUnreachable.class, "CONNECTING_FABRIC")

                .state("CONNECTING_FABRIC")
                    .interim()
                    .timeout(30, TimeUnit.SECONDS, "ERROR")
                    .onEntry(self -> self.markJoining("CONNECTING_FABRIC"))
                    .on(FabricReachable.class, "DETECTING_FACETS")

                .state("DETECTING_FACETS")
                    .interim()
                    .onEntry(self -> self.markJoining("DETECTING_FACETS"))
                    .on(FacetsDetected.class, "ARMING_INVESTIGATORS")
                    .on(FabricUnreachable.class, "CONNECTING_FABRIC")

                .state("ARMING_INVESTIGATORS")
                    .interim()
                    .onEntry(self -> self.markJoining("ARMING_INVESTIGATORS"))
                    .on(InvestigatorsArmed.class, "ALIVE")
                    .on(FabricUnreachable.class, "CONNECTING_FABRIC")

                .state("ALIVE")
                    .interim()
                    .onEntry(self -> self.markAlive())
                    .on(FabricUnreachable.class, "CONNECTING_FABRIC")
                    .on(DrainRequested.class, "DRAINING")

                .state("DRAINING")
                    .interim()
                    .timeout(15, TimeUnit.SECONDS, "STOPPED")
                    .onEntry(self -> self.markDraining())
                    .on(DrainRequested.class, "STOPPED")

                .state("STOPPED")
                    .terminal()
                    .outcomeOk()
                    .onEntry(self -> self.markStopped())

                .state("ERROR")
                    .terminal()
                    .outcomeFailure("could not reach fabric within startup window")
                    .onEntry(self -> self.markError())

                .build();
    }

    // ── inner methods called by the SM ──

    private void markJoining(String phase) {
        // The legacy AgentState only has JOINING/ALIVE/DRAINING/ERROR; map each
        // pre-ALIVE phase onto JOINING so callers (Heartbeat etc.) see a steady
        // "still booting" signal.
        if (state.status() != AgentState.Status.JOINING) {
            // already past JOINING — preserve it; phase change is visible via currentPhase()
        }
    }

    private void markAlive() {
        state.markAlive();
        LOG.infof("Agent fully alive: node=%s cluster=%s probes=%d",
                cfg.nodeName(), cfg.clusterName(), countRegisteredChecks());
    }

    private void markDraining() {
        state.markDraining();
    }

    private void markStopped() {
        LOG.info("Agent lifecycle terminated cleanly");
    }

    private void markError() {
        state.markError("fabric unreachable during startup");
    }

    private boolean isFabricReachable() {
        if (fabric instanceof EtcdFabric e) {
            try { return e.ping(); }
            catch (Exception ex) { return false; }
        }
        return true; // unknown fabric — optimistic, the heartbeat-publishing path will surface real errors
    }

    private int countRegisteredChecks() {
        int n = 0;
        for (HealthCheck ignored : healthChecks) n++;
        return n;
    }
}
