package com.tb.nw.core.sm;

import java.util.function.BiPredicate;

/**
 * One transition rule on a state: when an event of {@code eventClass} arrives
 * and the (optional) guard returns true, the SM moves to {@code target}.
 */
final class Transition<C, E> {

    private final Class<E> eventClass;
    private final String target;
    private final BiPredicate<C, E> guard;

    Transition(Class<E> eventClass, String target, BiPredicate<C, E> guard) {
        this.eventClass = eventClass;
        this.target = target;
        this.guard = guard;
    }

    String target() { return target; }

    @SuppressWarnings("unchecked")
    boolean matches(Object event, C context) {
        if (!eventClass.isInstance(event)) return false;
        if (guard == null) return true;
        return guard.test(context, (E) event);
    }
}
