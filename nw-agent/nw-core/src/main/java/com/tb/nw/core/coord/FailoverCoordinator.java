package com.tb.nw.core.coord;

import com.tb.nw.core.coord.events.MasterDeadDetected;
import com.tb.nw.core.coord.events.MasterFenced;
import com.tb.nw.core.coord.events.SlavePromoted;
import com.tb.nw.core.sm.StateMap;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level failover orchestrator. Written as a state machine so the top of
 * this class spells out the named business phases:
 *
 * <pre>
 *   WATCHING ──MasterDeadDetected──► FENCING_MASTER ──MasterFenced──► PROMOTING_SLAVE
 *                                          │                                │
 *                                      timeout/                          SlavePromoted
 *                                          ▼                                ▼
 *                                       FAILED                            DONE
 * </pre>
 *
 * For the current cut, {@code fenceMaster()} and {@code promoteSlave()} print
 * to console and synchronously fire their completion events. When the real
 * Dispatcher + ActionEndpoint land, those methods will issue etcd Txn +
 * mTLS gRPC calls; the SM shape stays identical.
 *
 * After reaching {@code DONE} or {@code FAILED}, the coordinator rebuilds a
 * fresh machine in {@code WATCHING} so a subsequent outage can trigger a new
 * failover.
 */
@Startup
@ApplicationScoped
public class FailoverCoordinator {

    private static final Logger LOG = Logger.getLogger(FailoverCoordinator.class);

    @Inject FailoverConfig cfg;

    private final AtomicReference<StateMap<FailoverCoordinator>> machine = new AtomicReference<>();
    private final AtomicReference<MasterDeadDetected> lastTrigger = new AtomicReference<>();

    @PostConstruct
    void init() {
        rebuildMachine();
        LOG.info("FailoverCoordinator armed in WATCHING");
    }

    @PreDestroy
    void shutdown() {
        StateMap<FailoverCoordinator> sm = machine.getAndSet(null);
        if (sm != null) sm.close();
    }

    /** Entry point from {@link FailureTracker} when the failure threshold is crossed. */
    public void declareTargetDead(MasterDeadDetected event) {
        StateMap<FailoverCoordinator> sm = machine.get();
        if (sm == null) {
            LOG.warn("FailoverCoordinator not initialized; ignoring " + event);
            return;
        }
        lastTrigger.set(event);
        boolean accepted = sm.fire(event);
        if (!accepted) {
            LOG.infof("Ignoring %s — coordinator in state %s, not WATCHING", event, sm.currentState());
        }
    }

    /** Exposed to /agent/info — visible current phase. */
    public String currentPhase() {
        StateMap<FailoverCoordinator> sm = machine.get();
        return sm == null ? "uninitialized" : sm.currentState();
    }

    public MasterDeadDetected lastTrigger() {
        return lastTrigger.get();
    }

    // ── state machine entry — names the business phases at the top ──

    private void rebuildMachine() {
        StateMap<FailoverCoordinator> previous = machine.get();
        if (previous != null) previous.close();

        StateMap<FailoverCoordinator> sm = StateMap.<FailoverCoordinator>builder()
                .name("FailoverCoordinator")
                .auditSink(ev -> LOG.infof("FailoverCoordinator: %s -> %s", ev.from(), ev.to()))
                .initialState("WATCHING")

                .state("WATCHING")
                    .interim()
                    .on(MasterDeadDetected.class, "FENCING_MASTER")

                .state("FENCING_MASTER")
                    .interim()
                    .timeout(cfg.fenceTimeoutSec(), TimeUnit.SECONDS, "FAILED_TIMEOUT")
                    .onEntry(self -> self.fenceMaster())
                    .on(MasterFenced.class, "PROMOTING_SLAVE")

                .state("PROMOTING_SLAVE")
                    .interim()
                    .timeout(cfg.promoteTimeoutSec(), TimeUnit.SECONDS, "FAILED_TIMEOUT")
                    .onEntry(self -> self.promoteSlave())
                    .on(SlavePromoted.class, "DONE")

                .state("DONE")
                    .terminal()
                    .outcomeOk()
                    .onEntry(self -> self.completed())

                .state("FAILED_TIMEOUT")
                    .terminal()
                    .outcomeFailure("step timed out")
                    .onEntry(self -> self.failed("timeout"))

                .build();

        sm.start(this);
        machine.set(sm);
    }

    // ── inner methods — the actual work, kept one level below the top ──

    private void fenceMaster() {
        MasterDeadDetected trig = lastTrigger.get();
        String target = trig != null ? trig.target() : "<unknown>";
        // Console line per the demo spec.
        System.out.println("Fencing master (" + target + ").");
        // Real impl: etcd Txn — set role to mysql-quarantined, increment epoch,
        // gRPC call to master's ActionEndpoint to set read_only + kill writers.
        StateMap<FailoverCoordinator> sm = machine.get();
        if (sm != null) sm.fire(new MasterFenced(target));
    }

    private void promoteSlave() {
        // For the demo, pick the slave deterministically from config; the real
        // CandidateSelector lands later.
        String newMaster = pickPromotionCandidate();
        // Console line per the demo spec.
        System.out.println("Promoting slave (" + newMaster + ") to master.");
        // Real impl: gRPC call to slave's ActionEndpoint — STOP REPLICA + RESET
        // REPLICA ALL + SET read_only=0, then etcd Txn to rotate role.
        StateMap<FailoverCoordinator> sm = machine.get();
        if (sm != null) sm.fire(new SlavePromoted(newMaster));
    }

    private void completed() {
        LOG.info("Failover completed — rebuilding coordinator to WATCHING");
        // Re-arm asynchronously so the SM doesn't recurse on its own thread.
        new Thread(this::rebuildMachine, "nw-coord-rearm").start();
    }

    private void failed(String reason) {
        LOG.warnf("Failover FAILED (%s) — rebuilding coordinator to WATCHING", reason);
        new Thread(this::rebuildMachine, "nw-coord-rearm").start();
    }

    private String pickPromotionCandidate() {
        // Placeholder — the real CandidateSelector reads ObservationView and
        // picks the slave with the highest LSN / lowest lag. For the demo,
        // we just announce "the slave".
        return "the-slave";
    }
}
