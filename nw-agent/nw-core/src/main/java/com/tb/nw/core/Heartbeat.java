package com.tb.nw.core;

import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Periodic proof-of-life signal. Writes /agents/{node}/heartbeat tied to the
 * shared agent lease so the entry vanishes automatically when the agent dies.
 */
@Startup
@ApplicationScoped
public class Heartbeat {

    private static final Logger LOG = Logger.getLogger(Heartbeat.class);

    @Inject AgentConfig cfg;
    @Inject AgentState state;
    @Inject AgentLease lease;
    @Inject Fabric fabric;

    @Scheduled(every = "5s", delayed = "2s")
    public void tick() {
        try {
            writeHeartbeat();
            state.recordHeartbeatOk();
            state.markAlive();
        } catch (FabricException e) {
            LOG.warnf("heartbeat publish failed: %s", e.getMessage());
            state.markError("heartbeat: " + e.getMessage());
        }
    }

    private void writeHeartbeat() {
        String key = "/agents/" + cfg.nodeName() + "/heartbeat";
        byte[] value = Instant.now().toString().getBytes(StandardCharsets.UTF_8);
        fabric.kv().put(key, value, lease.current());
    }
}
