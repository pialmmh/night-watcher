package com.tb.nw.spi;

import java.util.List;

/**
 * All observations for one {@code (target, vantage)} pair within the
 * Resolver's freshness window. The {@link HealthAggregator} scores this
 * bucket in isolation, producing a per-vantage health number that the
 * weighted reducer combines.
 *
 * @param <T> plugin-specific observation detail
 */
public record VantageBucket<T extends PluginEntity>(
        String target,
        Vantage vantage,
        List<Observation<T>> observations
) {}
