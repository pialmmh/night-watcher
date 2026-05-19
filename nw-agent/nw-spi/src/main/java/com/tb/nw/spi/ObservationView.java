package com.tb.nw.spi;

import java.util.List;

/**
 * A read-only window into one target's current observations across vantages.
 * Handed to the {@link CandidateSelector} when picking a promotion target.
 *
 * @param <T> plugin-specific observation detail
 */
public record ObservationView<T extends PluginEntity>(
        String target,
        List<Observation<T>> observations
) {}
