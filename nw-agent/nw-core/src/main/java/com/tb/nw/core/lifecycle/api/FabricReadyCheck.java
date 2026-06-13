package com.tb.nw.core.lifecycle.api;
import com.tb.nw.core.lifecycle.internal.AgentLifecycleMachine;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.internal.EtcdFabric;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Machine-shaped readiness for compose / k8s probes: GET /q/health/ready.
 * Ready = the fabric answers AND the lifecycle machine has reached a working
 * phase. The human-shaped status stays at /agent/info; liveness
 * (/q/health/live) is the default "process is up".
 */
@Readiness
@ApplicationScoped
public class FabricReadyCheck implements org.eclipse.microprofile.health.HealthCheck {

    @Inject Fabric fabric;
    @Inject AgentLifecycleMachine lifecycle;

    @Override
    public HealthCheckResponse call() {
        boolean fabricUp = fabric instanceof EtcdFabric e && e.ping();
        String phase = lifecycle.currentPhase();
        boolean phaseOk = "ALIVE".equals(phase) || "ARMING_INVESTIGATORS".equals(phase)
                || "DETECTING_FACETS".equals(phase);
        return HealthCheckResponse.named("nw-agent-ready")
                .status(fabricUp && phaseOk)
                .withData("fabric", fabricUp ? "reachable" : "unreachable")
                .withData("lifecycle", phase)
                .build();
    }
}
