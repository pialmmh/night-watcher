package com.tb.nw.spi;

/**
 * Resolver stage 8 plugin point. Builds the ordered Plan the Dispatcher
 * will execute step-by-step. The generator is the service-aware bit;
 * the Dispatcher walks the plan service-agnostically.
 *
 * @param <T> plugin {@link HealthCheckEvent} subtype consumed when reasoning about cluster state
 * @param <P> plugin {@link CommandEvent} subtype the Dispatcher hands to receiving actions
 */
public interface FailoverPlanGenerator<T extends HealthCheckEvent, P extends CommandEvent> {

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /**
     * Construct a Plan the Dispatcher can walk. Implementations include the
     * role-rotation ETCD_TXN step, the per-target ACTION_CALL command events,
     * and any cleanup. The Dispatcher itself does no service-specific reasoning.
     */
    Plan<P> generate(Verdict verdict, ClusterView<T> view);
}
