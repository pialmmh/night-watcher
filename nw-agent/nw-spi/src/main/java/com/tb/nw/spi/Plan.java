package com.tb.nw.spi;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A failover plan produced by a {@link FailoverPlanGenerator}. Steps are
 * intentionally narrow at the SPI level — the Dispatcher knows how to walk
 * them; plugin authors construct {@link PlanStep} instances via the static
 * factories the dispatcher exposes.
 *
 * <p>The {@code P} type parameter is the plugin's typed action payload —
 * the same type {@link FailoverAction} declares. ETCD_TXN steps carry
 * {@code null} payload; ACTION_CALL steps carry a typed {@code P} that the
 * receiving action consumes.</p>
 *
 * @param <P> plugin-specific action payload
 */
public record Plan<P extends PluginEntity>(
        String id,
        String cluster,
        long failoverEpoch,
        Instant createdAt,
        List<PlanStep<P>> steps,
        Map<String, Object> reasonChain
) {
    public record PlanStep<P extends PluginEntity>(
            int index,
            String kind,            // "ETCD_TXN" | "ACTION_CALL"
            String description,
            NodeId targetNode,      // null for ETCD_TXN
            String actionId,        // null for ETCD_TXN
            P payload,              // null for ETCD_TXN; typed for ACTION_CALL
            long timeoutMillis,
            int maxRetries
    ) {}
}
