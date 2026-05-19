package com.tb.nw.spi;

/**
 * Resolver stage 3 plugin point. Reduces all observations for one
 * {@code (target, vantage)} bucket to a single {@code 0.0–1.0} health score.
 *
 * <p>Implementations are pure functions; the same input must always produce
 * the same output so the shadow Resolver on followers reaches the same
 * verdict as the coordinator.</p>
 *
 * @param <T> plugin-specific observation detail
 */
public interface HealthAggregator<T extends PluginEntity> {

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /**
     * {@code 1.0} = perfectly healthy, {@code 0.0} = dead, intermediate = degraded.
     * Empty bucket → return {@link Double#NaN}; the reducer treats NaN as "no signal".
     */
    double scoreVantage(VantageBucket<T> bucket);
}
