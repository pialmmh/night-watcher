package com.tb.nw.core.coord;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * Knobs for the consensus + orchestration layer. Lives next to the
 * coordinator code rather than in the plugins so the threshold is
 * service-agnostic — the same logic applies regardless of which investigator
 * fires the DEAD verdict.
 */
@ConfigMapping(prefix = "nw.failover")
public interface FailoverConfig {

    /** Consecutive DEAD verdicts before declaring the target dead. */
    @WithDefault("3")
    int failureThreshold();

    /**
     * Investigator IDs whose DEAD verdict can trigger failover. Other
     * investigators (e.g. host.uptime) are still observed but don't drive
     * the FailoverCoordinator.
     */
    @WithDefault("mysql.remote.client")
    List<String> triggerInvestigators();

    /** Hard cap on the fence step — beyond this the SM transitions to FAILED. */
    @WithDefault("10")
    int fenceTimeoutSec();

    /** Hard cap on the promote step. */
    @WithDefault("10")
    int promoteTimeoutSec();
}
