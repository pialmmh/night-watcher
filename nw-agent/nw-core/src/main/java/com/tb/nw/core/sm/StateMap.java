package com.tb.nw.core.sm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Minimal state-machine DSL used by long-running daemons (AgentLifecycleMachine,
 * FailoverCoordinator). Modelled on the routesphere StateMap shape so reading
 * the top of any daemon class spells out its named business phases:
 *
 * <pre>
 *   StateMap&lt;Ctx&gt; sm = StateMap.&lt;Ctx&gt;builder()
 *       .initialState("WATCHING")
 *       .state("WATCHING")
 *           .interim()
 *           .onEntry(ctx -&gt; ctx.startWatching())
 *           .on(MasterDead.class, "FENCING_MASTER")
 *       .state("FENCING_MASTER")
 *           .interim()
 *           .timeout(10, TimeUnit.SECONDS, "FAILED")
 *           .onEntry(ctx -&gt; ctx.fenceMaster())
 *           .on(MasterFenced.class, "PROMOTING_SLAVE")
 *       .state("PROMOTING_SLAVE")
 *           .interim()
 *           .timeout(10, TimeUnit.SECONDS, "FAILED")
 *           .onEntry(ctx -&gt; ctx.promoteSlave())
 *           .on(SlavePromoted.class, "DONE")
 *       .state("DONE").terminal().outcomeOk()
 *       .state("FAILED").terminal().outcomeFailure("step timed out")
 *       .build();
 *
 *   sm.start(myContext);
 *   sm.fire(new MasterDead("kafka1"));
 * </pre>
 *
 * Thread-safety: {@link #fire(Object)} is synchronized. Re-entrant calls
 * from within {@code onEntry} (e.g. firing the next event after synchronous
 * work) are supported.
 *
 * Timeouts: each state may declare {@code .timeout(N, unit, "TARGET_STATE")}.
 * If no event lands within the window, the SM transitions to the target
 * state automatically. Timeouts cancel on any transition out of the state.
 */
public final class StateMap<C> implements AutoCloseable {

    private final Map<String, StateDef<C>> states;
    private final String initialState;
    private final String name;
    private final Consumer<TransitionEvent> auditSink;

    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private final AtomicReference<C> context = new AtomicReference<>();
    private final AtomicReference<String> current = new AtomicReference<>();
    private volatile ScheduledFuture<?> timeoutFuture;
    private volatile boolean started = false;

    StateMap(Map<String, StateDef<C>> states,
             String initialState,
             String name,
             Consumer<TransitionEvent> auditSink,
             ScheduledExecutorService externalScheduler) {
        this.states = states;
        this.initialState = initialState;
        this.name = name;
        this.auditSink = auditSink;
        if (externalScheduler != null) {
            this.scheduler = externalScheduler;
            this.ownsScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nw-sm-" + name);
                t.setDaemon(true);
                return t;
            });
            this.ownsScheduler = true;
        }
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    /** Drive the machine into its initial state. May only be called once. */
    public synchronized void start(C ctx) {
        if (started) {
            throw new IllegalStateException(name + ": already started");
        }
        this.context.set(ctx);
        this.started = true;
        enter(initialState);
    }

    /**
     * Submit an event. The first matching transition for the current state
     * fires; non-matching events are ignored (logged at debug level by callers
     * who want visibility).
     */
    public synchronized boolean fire(Object event) {
        if (!started) {
            throw new IllegalStateException(name + ": not started");
        }
        StateDef<C> def = states.get(current.get());
        if (def == null) return false;          // terminal: no transitions
        for (Transition<C, ?> t : def.transitions()) {
            if (t.matches(event, context.get())) {
                enter(t.target());
                return true;
            }
        }
        return false;
    }

    /** Current state name. Reads are non-blocking. */
    public String currentState() {
        return current.get();
    }

    public String name() { return name; }

    public boolean isTerminal() {
        StateDef<C> def = states.get(current.get());
        return def != null && def.isTerminal();
    }

    @Override
    public void close() {
        cancelTimeout();
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    // ── internals ──

    private void enter(String stateName) {
        StateDef<C> def = states.get(stateName);
        if (def == null) {
            throw new IllegalStateException(name + ": unknown target state " + stateName);
        }
        String prev = current.get();
        cancelTimeout();
        current.set(stateName);

        if (auditSink != null) {
            auditSink.accept(new TransitionEvent(name, prev, stateName));
        }

        Consumer<C> entry = def.onEntryHandler();
        if (entry != null) {
            try {
                entry.accept(context.get());
            } catch (RuntimeException e) {
                // Surface but do not corrupt SM state — terminal handlers may rely on us
                if (auditSink != null) {
                    auditSink.accept(new TransitionEvent(name, stateName, stateName + "(onEntry threw: " + e.getMessage() + ")"));
                }
                throw e;
            }
        }

        if (def.isTerminal()) return;

        if (def.timeoutMillis() > 0 && def.timeoutTarget() != null) {
            String target = def.timeoutTarget();
            this.timeoutFuture = scheduler.schedule(
                    () -> fireTimeout(stateName, target),
                    def.timeoutMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void fireTimeout(String fromState, String targetState) {
        if (!fromState.equals(current.get())) return;   // already moved on
        enter(targetState);
    }

    private void cancelTimeout() {
        ScheduledFuture<?> f = this.timeoutFuture;
        if (f != null) {
            f.cancel(false);
            this.timeoutFuture = null;
        }
    }

    /** Audit record emitted on every transition. */
    public record TransitionEvent(String machineName, String from, String to) {}

    // ── builder ──

    public static final class Builder<C> {
        private final Map<String, StateDef<C>> states = new LinkedHashMap<>();
        private String initialState;
        private String name = "anonymous-sm";
        private Consumer<TransitionEvent> auditSink;
        private ScheduledExecutorService scheduler;

        public Builder<C> name(String n) { this.name = n; return this; }
        public Builder<C> auditSink(Consumer<TransitionEvent> sink) { this.auditSink = sink; return this; }
        public Builder<C> scheduler(ScheduledExecutorService s) { this.scheduler = s; return this; }

        public Builder<C> initialState(String s) {
            this.initialState = s;
            return this;
        }

        public StateDef<C> state(String name) {
            StateDef<C> def = new StateDef<>(this, name);
            states.put(name, def);
            return def;
        }

        public StateMap<C> build() {
            if (initialState == null) {
                throw new IllegalStateException("initialState not set");
            }
            if (!states.containsKey(initialState)) {
                throw new IllegalStateException("initialState '" + initialState + "' not defined");
            }
            // validate every transition target exists
            for (StateDef<C> def : states.values()) {
                for (Transition<C, ?> t : def.transitions()) {
                    if (!states.containsKey(t.target())) {
                        throw new IllegalStateException(
                                "state '" + def.name() + "' transitions to undefined state '" + t.target() + "'");
                    }
                }
                if (def.timeoutTarget() != null && !states.containsKey(def.timeoutTarget())) {
                    throw new IllegalStateException(
                            "state '" + def.name() + "' timeout target '" + def.timeoutTarget() + "' is undefined");
                }
            }
            return new StateMap<>(states, initialState, name, auditSink, scheduler);
        }
    }
}
