package com.tb.nw.spi.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A failover plan produced by a {@link FailoverPlanGenerator}. The
 * Dispatcher walks the ordered steps and ships each one to its target.
 *
 * <p>Each {@link PlanStep} carries a typed {@link CommandEvent} when its
 * {@code kind} is {@code ACTION_CALL} — that's the event the receiving
 * agent dispatches to the matching {@link FailoverAction}.
 * {@code ETCD_TXN} steps carry no command; the Dispatcher executes the
 * embedded transaction directly.</p>
 *
 * @param <P> plugin-specific command event used by this plan's ACTION_CALL steps
 */
public record Plan<P extends CommandEvent>(
        String id,
        String cluster,
        long failoverEpoch,
        Instant createdAt,
        List<PlanStep<P>> steps,
        Map<String, Object> reasonChain
) {
    public record PlanStep<P extends CommandEvent>(
            int index,
            String kind,           // "ETCD_TXN" | "ACTION_CALL"
            String description,
            P command,             // null for ETCD_TXN; typed event for ACTION_CALL
            long timeoutMillis,
            int maxRetries
    ) {}
}
