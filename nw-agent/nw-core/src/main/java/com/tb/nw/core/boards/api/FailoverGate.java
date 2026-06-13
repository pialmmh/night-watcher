package com.tb.nw.core.boards.api;

import com.tb.nw.core.door.api.ActionEndpoint;

import com.tb.nw.core.dependencies.AgentConfig;
import com.tb.nw.core.lifecycle.internal.AgentLease;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricTxn;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The Gate — the cluster's "at most one fencer" guarantee, two keys on the
 * fabric:
 *
 * <ul>
 *   <li><b>lock</b> {@code /clusters/{c}/failover/lock} — created
 *       if-absent under THIS agent's lease. Exactly one creation wins; the
 *       key vanishes by itself when the holder dies (lease TTL).</li>
 *   <li><b>epoch</b> {@code /clusters/{c}/failover/epoch} — durable counter.
 *       The holder bumps it once per failover; every ActionEndpoint refuses
 *       commands minted in an older epoch.</li>
 * </ul>
 */
@ApplicationScoped
public class FailoverGate {

    private static final Logger LOG = Logger.getLogger(FailoverGate.class);

    @Inject AgentConfig cfg;
    @Inject AgentLease lease;
    @Inject Fabric fabric;

    /**
     * Try to take the Gate. Returns true when this agent now holds the lock
     * (including the re-entrant case where it already held it). Never blocks.
     */
    public boolean tryAcquire() {
        try {
            FabricTxn.TxnResponse r = fabric.txns().begin()
                    .ifModRevisionEquals(lockKey(), 0)              // key absent → we create
                    .thenPut(lockKey(), self(), lease.current())
                    .commit();
            if (r.succeeded()) {
                LOG.infof("failover gate acquired by %s", cfg.nodeName());
                return true;
            }
            boolean held = holder().map(cfg.nodeName()::equals).orElse(false);
            if (!held) LOG.infof("failover gate held by %s — standing down", holder().orElse("?"));
            return held;
        } catch (FabricException e) {
            LOG.warnf("failover gate unreachable (%s) — treating as not acquired", e.getMessage());
            return false;
        }
    }

    /** Release the Gate if this agent holds it. Safe to call when not holding. */
    public void release() {
        try {
            fabric.txns().begin()
                    .ifValueEquals(lockKey(), self())
                    .thenDelete(lockKey())
                    .commit();
        } catch (FabricException e) {
            LOG.warnf("failover gate release failed (%s) — lease expiry will clean up", e.getMessage());
        }
    }

    /** Current holder's node name, when the lock key exists. */
    public Optional<String> holder() {
        try {
            return fabric.kv().get(lockKey()).map(b -> new String(b, StandardCharsets.UTF_8));
        } catch (FabricException e) {
            return Optional.empty();
        }
    }

    /** Current epoch — 0 when never bumped. Throws when the fabric is unreadable. */
    public long epoch() {
        return fabric.kv().get(epochKey())
                .map(b -> Long.parseLong(new String(b, StandardCharsets.UTF_8)))
                .orElse(0L);
    }

    /** Bump and return the new epoch. Call ONLY while holding the Gate. */
    public long bumpEpoch() {
        long next = epoch() + 1;
        fabric.kv().put(epochKey(), Long.toString(next).getBytes(StandardCharsets.UTF_8));
        LOG.infof("failover epoch bumped to %d", next);
        return next;
    }

    private byte[] self() {
        return cfg.nodeName().getBytes(StandardCharsets.UTF_8);
    }

    private String lockKey()  { return "/clusters/" + cfg.clusterName() + "/failover/lock"; }
    private String epochKey() { return "/clusters/" + cfg.clusterName() + "/failover/epoch"; }
}
