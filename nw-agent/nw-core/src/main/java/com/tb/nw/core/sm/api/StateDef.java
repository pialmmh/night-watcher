package com.tb.nw.core.sm.api;
import com.tb.nw.core.sm.api.StateMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * One named state in a {@link StateMap}. Built fluently from
 * {@code builder.state(name).onEntry(...).on(EventClass, "TARGET")}.
 *
 * The fluent chain returns this same StateDef so adjacent {@code .on(...)} /
 * {@code .onEntry(...)} / {@code .timeout(...)} calls accumulate on the same
 * state. Calling {@code .state("next")} on this object pivots the chain to a
 * new state — sugar for going back to the builder.
 */
public final class StateDef<C> {

    private final StateMap.Builder<C> owner;
    private final String name;
    private final List<Transition<C, ?>> transitions = new ArrayList<>();

    private boolean terminal = false;
    private String terminalOutcome;          // "ok" | "failure"
    private String terminalFailureReason;

    private Consumer<C> onEntry;
    private long timeoutMillis;
    private String timeoutTarget;

    StateDef(StateMap.Builder<C> owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public String name() { return name; }
    public boolean isTerminal() { return terminal; }
    public String terminalOutcome() { return terminalOutcome; }
    public String terminalFailureReason() { return terminalFailureReason; }
    public Consumer<C> onEntryHandler() { return onEntry; }
    public long timeoutMillis() { return timeoutMillis; }
    public String timeoutTarget() { return timeoutTarget; }
    public List<Transition<C, ?>> transitions() { return transitions; }

    /** Marks this state as non-terminal (transitions may follow). Default. */
    public StateDef<C> interim() {
        this.terminal = false;
        return this;
    }

    /** Marks this state as terminal — no further transitions; SM is at rest. */
    public StateDef<C> terminal() {
        this.terminal = true;
        return this;
    }

    public StateDef<C> outcomeOk() {
        this.terminal = true;
        this.terminalOutcome = "ok";
        return this;
    }

    public StateDef<C> outcomeFailure(String reason) {
        this.terminal = true;
        this.terminalOutcome = "failure";
        this.terminalFailureReason = reason;
        return this;
    }

    public StateDef<C> timeout(long n, TimeUnit unit, String onTimeoutState) {
        this.timeoutMillis = unit.toMillis(n);
        this.timeoutTarget = onTimeoutState;
        return this;
    }

    public StateDef<C> onEntry(Consumer<C> action) {
        this.onEntry = action;
        return this;
    }

    public <E> StateDef<C> on(Class<E> eventClass, String targetState) {
        transitions.add(new Transition<>(eventClass, targetState, null));
        return this;
    }

    public <E> StateDef<C> on(Class<E> eventClass, String targetState, BiPredicate<C, E> guard) {
        transitions.add(new Transition<>(eventClass, targetState, guard));
        return this;
    }

    /** Pivot the fluent chain to define another state — sugar for {@code .build().state(name)}. */
    public StateDef<C> state(String name) {
        return owner.state(name);
    }

    public StateMap<C> build() {
        return owner.build();
    }
}
