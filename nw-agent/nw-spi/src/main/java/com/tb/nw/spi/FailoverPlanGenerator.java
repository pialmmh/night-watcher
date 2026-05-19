package com.tb.nw.spi;

/**
 * Resolver stage 8 plugin point. Builds the ordered Plan the Dispatcher
 * will execute step-by-step. The generator is the service-aware bit;
 * the Dispatcher walks the plan service-agnostically.
 *
 * @param <T> plugin observation detail consumed when reasoning about cluster state
 * @param <P> plugin action payload that the Dispatcher will hand to the receiving action
 */
public interface FailoverPlanGenerator<T extends PluginEntity, P extends PluginEntity> {

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /**
     * Construct a Plan that the Dispatcher can walk. Implementations must
     * include the role-rotation ETCD_TXN step, the per-target ACTION_CALL
     * steps, and any cleanup. The Dispatcher itself does no service-specific
     * reasoning.
     */
    Plan<P> generate(Verdict verdict, ClusterView<T> view);
}
