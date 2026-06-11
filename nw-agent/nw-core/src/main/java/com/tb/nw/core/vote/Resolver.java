package com.tb.nw.core.vote;

import com.tb.nw.core.coordinator.FailoverConfig;

import com.tb.nw.core.cache.ObservationCache;
import com.tb.nw.spi.HealthAggregator;
import com.tb.nw.spi.HealthCheckEvent;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.Vantage;
import com.tb.nw.spi.VantageBucket;
import com.tb.nw.spi.Verdict;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The vote — reduces the cache's evidence to one {@link Verdict} per target.
 *
 * <p>Deterministic by construction: a pure function of (cache content, now),
 * so every agent running the same Resolver over the same board reaches the
 * same verdict — the follower shadow IS the coordinator's calculation.</p>
 *
 * <p>Stages, per target:</p>
 * <ol>
 *   <li>Drop observations older than {@code resolver-freshness-sec}.</li>
 *   <li>Group the rest into {@link VantageBucket}s — one per seat.</li>
 *   <li>The owning plugin's {@link HealthAggregator} scores each bucket
 *       {@code 0.0–1.0} ({@code NaN} = seat silent).</li>
 *   <li>Reduce seats to a verdict: a seat ≤ {@value #DOWN_CEILING} says
 *       "down". A strict majority of ≥2 reporting seats down → {@code ODOWN};
 *       any seat down → {@code SDOWN}; any seat midband → {@code DEGRADED};
 *       else {@code UP}.</li>
 * </ol>
 */
@ApplicationScoped
public class Resolver {

    private static final Logger LOG = Logger.getLogger(Resolver.class);

    /** A vantage score at or below this means the seat says "down". */
    static final double DOWN_CEILING = 0.25;
    /** A vantage score below this (and above the down ceiling) means "degraded". */
    static final double HEALTHY_FLOOR = 0.75;

    @Inject ObservationCache cache;
    @Inject FailoverConfig cfg;
    @Inject Instance<HealthAggregator<?>> aggregators;

    /** One target's reduced state — scores keyed by seat, NaN-free. */
    public record TargetVerdict(
            String target,
            Verdict verdict,
            Map<Vantage, Double> seatScores,
            int seatsReporting,
            int seatsDown) {}

    /** Verdict for one target — empty when no seat reports fresh evidence. */
    public Optional<Verdict> verdictFor(String target) {
        TargetVerdict tv = all().get(target);
        return tv == null ? Optional.empty() : Optional.of(tv.verdict());
    }

    /** Every target's verdict, recomputed from the live cache. */
    public Map<String, TargetVerdict> all() {
        Instant floor = Instant.now().minus(Duration.ofSeconds(cfg.resolverFreshnessSec()));
        Map<String, Map<Vantage, Map<String, List<Observation<?>>>>> grouped = groupFresh(floor);

        Map<String, TargetVerdict> out = new LinkedHashMap<>();
        for (var perTarget : grouped.entrySet()) {
            TargetVerdict tv = reduceTarget(perTarget.getKey(), perTarget.getValue());
            if (tv != null) out.put(perTarget.getKey(), tv);
        }
        return out;
    }

    // ── stage 1+2 — fresh evidence, bucketed per (target, seat, plugin) ──
    // Plugin in the key because aggregators are plugin-scoped: one seat can
    // carry several plugins' evidence (host.uptime next to a service probe),
    // and each plugin only scores its own.

    private Map<String, Map<Vantage, Map<String, List<Observation<?>>>>> groupFresh(Instant floor) {
        Map<String, Map<Vantage, Map<String, List<Observation<?>>>>> grouped = new HashMap<>();
        for (Observation<?> o : cache.all()) {
            if (o.freshness().isBefore(floor)) continue;
            grouped.computeIfAbsent(o.target(), k -> new EnumMap<>(Vantage.class))
                    .computeIfAbsent(o.vantage(), k -> new HashMap<>())
                    .computeIfAbsent(o.pluginId(), k -> new ArrayList<>())
                    .add(o);
        }
        return grouped;
    }

    // ── stage 3+4 — score each seat, reduce to a verdict ──

    private TargetVerdict reduceTarget(String target, Map<Vantage, Map<String, List<Observation<?>>>> seats) {
        Map<Vantage, Double> scores = new EnumMap<>(Vantage.class);
        for (var seat : seats.entrySet()) {
            // A seat's score = the worst opinion among the plugins watching
            // this target from this seat (plugins without an aggregator stay
            // silent). Deterministic: min over a set.
            double worst = Double.NaN;
            for (var perPlugin : seat.getValue().entrySet()) {
                double s = scoreBucket(target, seat.getKey(), perPlugin.getValue());
                if (Double.isNaN(s)) continue;
                worst = Double.isNaN(worst) ? s : Math.min(worst, s);
            }
            if (!Double.isNaN(worst)) scores.put(seat.getKey(), worst);
        }
        if (scores.isEmpty()) return null;

        int reporting = scores.size();
        int down = (int) scores.values().stream().filter(s -> s <= DOWN_CEILING).count();
        boolean degraded = scores.values().stream().anyMatch(s -> s > DOWN_CEILING && s < HEALTHY_FLOOR);

        Verdict verdict;
        if (down >= 1) {
            verdict = (reporting >= 2 && down * 2 > reporting) ? Verdict.ODOWN : Verdict.SDOWN;
        } else if (degraded) {
            verdict = Verdict.DEGRADED;
        } else {
            verdict = Verdict.UP;
        }
        return new TargetVerdict(target, verdict, scores, reporting, down);
    }

    /**
     * Erased bridge — the bucket's observations and the aggregator are
     * matched by pluginId, so the typed views agree by plugin construction.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private double scoreBucket(String target, Vantage vantage, List<Observation<?>> obs) {
        HealthAggregator agg = aggregatorFor(obs.get(0).pluginId());
        if (agg == null) {
            LOG.debugf("no HealthAggregator for plugin %s — seat %s/%s silent",
                    obs.get(0).pluginId(), target, vantage);
            return Double.NaN;
        }
        try {
            return agg.scoreVantage(new VantageBucket(target, vantage, obs));
        } catch (Exception e) {
            LOG.warnf("aggregator %s threw for %s/%s: %s — seat silent",
                    obs.get(0).pluginId(), target, vantage, e.getMessage());
            return Double.NaN;
        }
    }

    private HealthAggregator<? extends HealthCheckEvent> aggregatorFor(String pluginId) {
        for (HealthAggregator<?> a : aggregators) {
            if (pluginId.equals(a.descriptor().pluginId())) return a;
        }
        return null;
    }
}
