package com.tb.nw.spi.api;

import java.util.List;

/**
 * All observations for one {@code (target, vantage)} pair within the
 * Resolver's freshness window. The {@link HealthAggregator} scores this
 * bucket in isolation, producing a per-vantage health number that the
 * weighted reducer combines.
 *
 * @param <T> plugin-specific {@link HealthCheckEvent} subtype
 */
public record VantageBucket<T extends HealthCheckEvent>(
        String target,
        Vantage vantage,
        List<Observation<T>> observations
) {}
