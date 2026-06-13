package com.tb.nw.plugins.mysql.internal;

import com.tb.nw.spi.api.HealthState;

/**
 * Pure role-aware grading for the LOCAL_SELF probe — testable without a live
 * MySQL. The local scout self-detects its role from the readings:
 *
 * <ul>
 *   <li><b>Role 1 (master self-watch)</b> — no replica threads. Healthy (FAST)
 *       when alive and not unexpectedly read-only; a master that flipped to
 *       read-only is DEGRADED.</li>
 *   <li><b>Role 2 (slave self-watch)</b> — replica threads present. FAST only
 *       when {@link #promotable promotable} (threads running + lag within the
 *       window — "does not lack in binlog"); otherwise DEGRADED.</li>
 * </ul>
 *
 * <p>Gross slowness drops either role to DEGRADED. DEAD is decided by the probe
 * (a connection failure), never here.</p>
 */
public final class MysqlLocalGrader {

    private MysqlLocalGrader() {}

    public static HealthState grade(long latencyMs, Boolean readOnly,
            Boolean replicaIo, Boolean replicaSql, Long secondsBehind,
            long degradedThresholdMs, long maxPromotionLagSec) {
        boolean slave = replicaIo != null || replicaSql != null || secondsBehind != null;
        if (slave) {
            if (!promotable(replicaIo, replicaSql, secondsBehind, maxPromotionLagSec)) return HealthState.DEGRADED;
            return latencyMs <= degradedThresholdMs ? HealthState.FAST : HealthState.DEGRADED;
        }
        if (Boolean.TRUE.equals(readOnly)) return HealthState.DEGRADED;   // a master should not be read-only
        return latencyMs <= degradedThresholdMs ? HealthState.FAST : HealthState.DEGRADED;
    }

    public static boolean promotable(Boolean replicaIo, Boolean replicaSql, Long secondsBehind, long maxLagSec) {
        return Boolean.TRUE.equals(replicaIo) && Boolean.TRUE.equals(replicaSql)
                && secondsBehind != null && secondsBehind <= maxLagSec;
    }
}
