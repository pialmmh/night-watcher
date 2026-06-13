package com.tb.nw.spi.api;

import java.time.Instant;
import java.util.Map;

public record ActionResult(
        boolean ok,
        String reason,
        Map<String, Object> detail,
        Instant completedAt
) {
    public static ActionResult ok(Map<String, Object> detail) {
        return new ActionResult(true, null, detail, Instant.now());
    }

    public static ActionResult failure(String reason) {
        return new ActionResult(false, reason, Map.of(), Instant.now());
    }

    public static ActionResult failure(String reason, Map<String, Object> detail) {
        return new ActionResult(false, reason, detail, Instant.now());
    }
}
