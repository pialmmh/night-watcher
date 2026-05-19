package com.tb.nw.spi;

import java.util.List;
import java.util.Optional;

/**
 * Resolver stage 7 plugin point. Picks a promotion candidate from the
 * available pool when the Resolver decides a failover is warranted.
 *
 * <p>Implementations are pure functions: same inputs → same chosen NodeId,
 * so coordinator and follower-shadow agree.</p>
 *
 * @param <T> plugin-specific observation detail (what's inside each ObservationView)
 */
public interface CandidateSelector<T extends PluginEntity> {

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /**
     * Return the NodeId to promote, or empty if no candidate is viable
     * (in which case the Resolver halts the failover and alerts).
     */
    Optional<NodeId> select(List<ObservationView<T>> candidates, SelectionContext ctx);
}
