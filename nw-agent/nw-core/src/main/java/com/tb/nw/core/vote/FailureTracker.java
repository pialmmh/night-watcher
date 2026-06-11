package com.tb.nw.core.vote;

import com.tb.nw.core.coordinator.FailoverConfig;
import com.tb.nw.core.coordinator.FailoverCoordinator;

import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.core.coordinator.events.MasterDeadDetected;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.Observation;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consecutive-failure counter — the simplest possible SDOWN/ODOWN layer for
 * the demo. Listens on {@link ObservationCache} change events; for each
 * (target, investigator) pair, counts consecutive {@link HealthState#DEAD}
 * verdicts and resets on anything else.
 *
 * On crossing {@link FailoverConfig#failureThreshold()}, fires a
 * {@link MasterDeadDetected} event into the {@link FailoverCoordinator}
 * state machine and resets the counter so the trigger is one-shot per
 * outage.
 *
 * Only counts observations whose investigator id is in
 * {@link FailoverConfig#triggerInvestigators()} — so host.uptime and other
 * non-failover signals are watched in the cache but don't drive the SM.
 */
@Startup
@ApplicationScoped
public class FailureTracker {

    private static final Logger LOG = Logger.getLogger(FailureTracker.class);

    @Inject ObservationCache cache;
    @Inject FailoverConfig cfg;
    @Inject FailoverCoordinator coordinator;
    @Inject Resolver resolver;

    private final ConcurrentHashMap<ObservationCache.ObsKey, AtomicInteger> counters = new ConcurrentHashMap<>();
    private Set<String> triggerInvestigatorIds;

    @PostConstruct
    void init() {
        this.triggerInvestigatorIds = new HashSet<>(cfg.triggerInvestigators());
        cache.addListener(this::onChange);
        LOG.infof("FailureTracker armed: threshold=%d, triggers=%s",
                cfg.failureThreshold(), triggerInvestigatorIds);
    }

    void onChange(ObservationCache.ChangeEvent ev) {
        Observation<?> obs = ev.observation();
        if (obs == null) return;
        if (!triggerInvestigatorIds.contains(obs.investigator())) return;
        if (ev.kind() == ObservationCache.ChangeEvent.Kind.REMOVED) {
            // Lease expired — treat as one more dead signal? For the simple
            // demo, no: only count probes that actually classified DEAD.
            return;
        }

        ObservationCache.ObsKey key = ev.key();

        if (obs.state() == HealthState.DEAD) {
            int n = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            LOG.infof("DEAD count for %s/%s = %d/%d",
                    key.target(), key.investigator(), n, cfg.failureThreshold());
            if (n >= cfg.failureThreshold()) {
                if (cfg.requireOdown() && !quorumAgrees(obs.target())) {
                    // Keep the counter — the next DEAD re-evaluates the vote.
                    return;
                }
                counters.remove(key);
                coordinator.declareTargetDead(new MasterDeadDetected(
                        obs.target(), obs.investigator(), n));
            }
        } else if (obs.state() == HealthState.FAST || obs.state() == HealthState.DEGRADED) {
            AtomicInteger prev = counters.remove(key);
            if (prev != null && prev.get() > 0) {
                LOG.debugf("DEAD streak reset for %s/%s (was %d, state=%s)",
                        key.target(), key.investigator(), prev.get(), obs.state());
            }
        }
        // UNKNOWN: no signal — neither count nor reset.
    }

    /**
     * The cross-vantage gate (require-odown mode): strikes alone don't fire
     * the coordinator — a majority of reporting seats must agree the target
     * is down (Resolver verdict ODOWN).
     */
    private boolean quorumAgrees(String target) {
        var verdict = resolver.verdictFor(target).orElse(null);
        if (verdict == com.tb.nw.spi.Verdict.ODOWN) return true;
        LOG.infof("strike threshold hit for %s but resolver says %s (require-odown) — holding",
                target, verdict == null ? "no-evidence" : verdict);
        return false;
    }

    /** Inspect current streak length — used by /agent/info. */
    public int streakFor(String target, String publisher, String investigator) {
        AtomicInteger n = counters.get(new ObservationCache.ObsKey(target, publisher, investigator));
        return n == null ? 0 : n.get();
    }
}
