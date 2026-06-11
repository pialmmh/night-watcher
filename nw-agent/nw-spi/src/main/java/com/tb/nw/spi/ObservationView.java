package com.tb.nw.spi;

import java.util.List;

/**
 * A read-only window into one target's current observations across vantages.
 * Handed to the {@link CandidateSelector} when picking a promotion target.
 *
 * @param <T> plugin-specific {@link HealthCheckEvent} subtype
 */
public record ObservationView<T extends HealthCheckEvent>(
        String target,
        List<Observation<T>> observations
) {}
