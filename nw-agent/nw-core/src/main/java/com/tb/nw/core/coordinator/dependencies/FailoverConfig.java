package com.tb.nw.core.coordinator.dependencies;

import com.tb.nw.core.vote.api.Resolver;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

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

    /** Deadline handed to a FailoverAction execution on the target agent. */
    @WithDefault("8")
    int actionDeadlineSec();

    /**
     * When true, a strike-threshold crossing only fires the coordinator if
     * the Resolver's cross-vantage verdict for the target is ODOWN (a
     * majority of reporting seats agree it is down). False = today's
     * single-stream behavior, unchanged.
     */
    @WithDefault("false")
    boolean requireOdown();

    /** Observations older than this are invisible to the Resolver. */
    @WithDefault("30")
    int resolverFreshnessSec();

    /**
     * Plugin id whose FailoverPlanGenerator builds the plan. Unset = the
     * first (only) deployed generator — correct for single-plugin agents;
     * set it when more than one plugin ships a generator.
     */
    Optional<String> planPlugin();
}
