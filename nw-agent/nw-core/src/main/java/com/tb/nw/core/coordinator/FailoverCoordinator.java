package com.tb.nw.core.coordinator;

import com.tb.nw.core.vote.FailureTracker;

import com.tb.nw.core.AgentConfig;
import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.core.dispatch.Dispatcher;
import com.tb.nw.core.boards.FailoverGate;
import com.tb.nw.core.PluginRegistry;
import com.tb.nw.core.boards.RoleStore;
import com.tb.nw.core.coordinator.events.MasterDeadDetected;
import com.tb.nw.core.coordinator.events.MasterFenced;
import com.tb.nw.core.coordinator.events.SlavePromoted;
import com.tb.nw.core.sm.StateMap;
import com.tb.nw.spi.ClusterType;
import com.tb.nw.spi.ClusterView;
import com.tb.nw.spi.CommandEvent;
import com.tb.nw.spi.CommandResultEvent;
import com.tb.nw.spi.FailoverPlanGenerator;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.Plan;
import com.tb.nw.spi.Verdict;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * <p>The act path (stories: "The act path — from dead verdict to a new
 * master"): on the first DEAD declaration the coordinator races for the
 * {@link FailoverGate} (one fencer only), bumps the epoch, asks the plugin's
 * {@link FailoverPlanGenerator} for the ordered plan, then walks it —
 * fence-phase steps during FENCING_MASTER (best-effort dispatch + durable
 * quarantine on the {@link RoleStore}), the final step during
 * PROMOTING_SLAVE (must succeed). Convention: <b>the last plan step is the
 * promotion</b>; everything before it is fencing.</p>
 *
 * <p>After reaching {@code DONE} or {@code FAILED}, the coordinator releases
 * the Gate and rebuilds a fresh machine in {@code WATCHING} so a subsequent
 * outage can trigger a new failover.</p>
 */
@Startup
@ApplicationScoped
public class FailoverCoordinator {

    private static final Logger LOG = Logger.getLogger(FailoverCoordinator.class);

    @Inject FailoverConfig cfg;
    @Inject AgentConfig agentCfg;
    @Inject FailoverGate gate;
    @Inject RoleStore roles;
    @Inject ObservationCache cache;
    @Inject Dispatcher dispatcher;
    @Inject Instance<FailoverPlanGenerator<?, ?>> planGenerators;
    @Inject com.tb.nw.core.vote.Resolver resolver;

    private final AtomicReference<StateMap<FailoverCoordinator>> machine = new AtomicReference<>();
    private final AtomicReference<MasterDeadDetected> lastTrigger = new AtomicReference<>();
    private final AtomicReference<Plan<?>> activePlan = new AtomicReference<>();

    /**
     * All orchestration runs here — onEntry hooks do blocking fabric txns and
     * multi-second dispatches, and the triggering ChangeEvent arrives on the
     * etcd watch (vert.x event-loop) thread, which must NEVER block.
     */
    private final ExecutorService coordThread =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "nw-coord"));

    @PostConstruct
    void init() {
        rebuildMachine();
        LOG.info("FailoverCoordinator armed in WATCHING");
    }

    @PreDestroy
    void shutdown() {
        coordThread.shutdownNow();
        StateMap<FailoverCoordinator> sm = machine.getAndSet(null);
        if (sm != null) sm.close();
    }

    /**
     * Entry point from {@link FailureTracker} when the failure threshold is
     * crossed. Hops off the caller's (watcher) thread immediately.
     */
    public void declareTargetDead(MasterDeadDetected event) {
        if (machine.get() == null) {
            LOG.warn("FailoverCoordinator not initialized; ignoring " + event);
            return;
        }
        lastTrigger.set(event);
        coordThread.submit(() -> {
            // Re-read on the coord thread — a re-arm may have swapped the machine.
            StateMap<FailoverCoordinator> sm = machine.get();
            if (sm == null || sm.isTerminal()) return;
            boolean accepted = sm.fire(event);
            if (!accepted) {
                LOG.infof("Ignoring %s — coordinator in state %s, not WATCHING", event, sm.currentState());
            }
        });
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
        activePlan.set(null);

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
        // Close the old machine only AFTER the new one is visible — a
        // queued trigger must never land on a closed StateMap (its timeout
        // scheduler is terminated and onEntry scheduling would throw).
        if (previous != null) previous.close();
    }

    // ── FENCING_MASTER — take the gate, plan, fence ──

    private void fenceMaster() {
        MasterDeadDetected trig = lastTrigger.get();
        String target = trig != null ? trig.target() : "<unknown>";

        // An already-quarantined target is an outage already handled — its
        // deadness is EXPECTED. Without this guard the watchers keep striking
        // the fenced corpse and the coordinator re-runs forever.
        if (roles.rolesOf(target).contains(RoleStore.QUARANTINED)) {
            LOG.infof("%s is already quarantined on the roles board — outage already handled, standing down", target);
            return;                                       // stall → FAILED_TIMEOUT → re-arm
        }

        if (!acquireGateOrStandDown()) return;            // losers stall → FAILED_TIMEOUT → re-arm
        long epoch = gate.bumpEpoch();

        Plan<?> plan = generatePlan(epoch);
        if (plan == null || plan.steps().isEmpty()) {
            LOG.warn("no failover plan available — stalling to FAILED");
            return;
        }
        activePlan.set(plan);

        // Console line per the demo spec.
        System.out.println("Fencing master (" + target + ").");

        executeFencePhase(plan);
        fire(new MasterFenced(target));
    }

    private boolean acquireGateOrStandDown() {
        if (gate.tryAcquire()) return true;
        LOG.info("another agent leads this failover — standing down");
        return false;
    }

    /**
     * Every step before the last is fencing. Dispatch is best-effort — the
     * usual reason a master needs fencing is that its box is unreachable;
     * the DURABLE fence is the roles-board quarantine, which always happens.
     */
    private void executeFencePhase(Plan<?> plan) {
        List<? extends Plan.PlanStep<?>> steps = plan.steps();
        for (int i = 0; i < steps.size() - 1; i++) {
            Plan.PlanStep<?> step = steps.get(i);
            CommandEvent cmd = step.command();
            if (cmd == null) {
                LOG.infof("skipping non-action fence step: %s", step.description());
                continue;
            }
            roles.put(cmd.targetNode(), RoleStore.QUARANTINED);
            dispatchBestEffort(step, fenceBudget());
        }
        if (steps.size() == 1) {
            LOG.info("plan has no fence step (no master agent known) — durable quarantine skipped");
        }
    }

    private void dispatchBestEffort(Plan.PlanStep<?> step, Duration budget) {
        CommandEvent cmd = step.command();
        try {
            CommandResultEvent result = dispatcher.send(cmd, budget);
            LOG.infof("fence step '%s' on %s → ok=%s (%s)",
                    step.description(), cmd.targetNode(), result.ok(), result.reason());
        } catch (Exception e) {
            LOG.warnf("fence step '%s' undeliverable (%s) — proceeding, quarantine on the roles board is the fence",
                    step.description(), e.getMessage());
        }
    }

    // ── PROMOTING_SLAVE — the last plan step must succeed ──

    private void promoteSlave() {
        Plan<?> plan = activePlan.get();
        if (plan == null || plan.steps().isEmpty()) {
            LOG.warn("no active plan in PROMOTING_SLAVE — stalling to FAILED");
            return;
        }
        Plan.PlanStep<?> step = plan.steps().get(plan.steps().size() - 1);
        CommandEvent cmd = step.command();
        if (cmd == null) {
            LOG.warn("final plan step carries no command — stalling to FAILED");
            return;
        }
        String newMaster = cmd.targetNode();

        // Console line per the demo spec.
        System.out.println("Promoting slave (" + newMaster + ") to master.");

        try {
            CommandResultEvent result = dispatcher.send(cmd, promoteBudget());
            if (!result.ok()) {
                LOG.warnf("promote refused/failed on %s: %s — stalling to FAILED", newMaster, result.reason());
                return;
            }
        } catch (Exception e) {
            LOG.warnf("promote undeliverable on %s (%s) — stalling to FAILED", newMaster, e.getMessage());
            return;
        }

        roles.put(newMaster, RoleStore.MASTER);
        fire(new SlavePromoted(newMaster));
    }

    // ── terminal states — always release the gate, always re-arm ──

    private void completed() {
        LOG.info("Failover completed — releasing gate, rebuilding coordinator to WATCHING");
        gate.release();
        rearmAsync();
    }

    private void failed(String reason) {
        LOG.warnf("Failover FAILED (%s) — releasing gate, rebuilding coordinator to WATCHING", reason);
        gate.release();
        rearmAsync();
    }

    // ── plan generation (plugin hook) ──

    /**
     * Erased bridge — the generator's typed view/plan parameters are
     * guaranteed consistent by the plugin's own construction; the
     * coordinator walks the plan via the type-agnostic CommandEvent surface.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Plan<?> generatePlan(long epoch) {
        FailoverPlanGenerator generator = pickGenerator();
        if (generator == null) {
            LOG.warn("no FailoverPlanGenerator deployed — cannot build a plan");
            return null;
        }
        ClusterView view = new ClusterView(
                agentCfg.clusterName(),
                resolveClusterType(),
                epoch,
                roles.all(),
                observationsForPlugin(generator.descriptor().pluginId()));
        try {
            return generator.generate(currentVerdict(), view);
        } catch (Exception e) {
            LOG.warnf(e, "plan generation failed");
            return null;
        }
    }

    /** The Resolver's live verdict for the triggering target; SDOWN when unscored. */
    private Verdict currentVerdict() {
        MasterDeadDetected trig = lastTrigger.get();
        if (trig == null) return Verdict.SDOWN;
        return resolver.verdictFor(trig.target()).orElse(Verdict.SDOWN);
    }

    private FailoverPlanGenerator<?, ?> pickGenerator() {
        var wanted = cfg.planPlugin();
        FailoverPlanGenerator<?, ?> first = null;
        for (FailoverPlanGenerator<?, ?> g : planGenerators) {
            if (wanted.isPresent() && wanted.get().equals(g.descriptor().pluginId())) return g;
            if (first == null) first = g;
        }
        if (wanted.isPresent() && first != null) {
            LOG.warnf("plan-plugin '%s' not deployed — falling back to '%s'",
                    wanted.get(), first.descriptor().pluginId());
        }
        return first;                                    // selection by clusterType later
    }

    private List<Observation<?>> observationsForPlugin(String pluginId) {
        List<Observation<?>> out = new ArrayList<>();
        for (Observation<?> o : cache.all()) {
            if (pluginId.equals(o.pluginId())) out.add(o);
        }
        return out;
    }

    private ClusterType resolveClusterType() {
        return ClusterType.parse(agentCfg.clusterType()).orElse(ClusterType.GENERIC);
    }

    // ── plumbing ──

    private void fire(Object event) {
        StateMap<FailoverCoordinator> sm = machine.get();
        if (sm != null) sm.fire(event);
    }

    /** Dispatch budgets stay inside the SM phase timeouts (2 s margin, ≥1 s). */
    private Duration fenceBudget() {
        return Duration.ofSeconds(Math.max(1, cfg.fenceTimeoutSec() - 2));
    }

    private Duration promoteBudget() {
        return Duration.ofSeconds(Math.max(1, cfg.promoteTimeoutSec() - 2));
    }

    private void rearmAsync() {
        // Re-arm asynchronously so the SM doesn't recurse on its own thread.
        new Thread(this::rebuildMachine, "nw-coord-rearm").start();
    }
}
