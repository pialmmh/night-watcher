package com.tb.nw.spi;

import java.time.Duration;
import java.util.Map;

/**
 * Handed to every FailoverAction.execute() invocation. Carries identity, epoch,
 * idempotency token, and the payload supplied by the Dispatcher.
 */
public record ActionContext(
        String clusterName,
        String localNode,
        long failoverEpoch,
        String idempotencyToken,
        Duration deadline,
        Map<String, Object> payload
) {}
