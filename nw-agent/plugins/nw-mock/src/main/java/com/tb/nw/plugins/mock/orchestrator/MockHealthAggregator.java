package com.tb.nw.plugins.mock.orchestrator;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.plugins.mock.events.MockHealth;
import com.tb.nw.spi.api.HealthAggregator;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.VantageBucket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * Scores one (target, vantage) bucket for the Resolver. Pure function:
 * freshest observation per publisher, mapped FAST=1.0 · DEGRADED=0.5 ·
 * DEAD=0.0 (UNKNOWN ignored), averaged. Empty bucket → NaN ("seat silent").
 */
@ApplicationScoped
public class MockHealthAggregator implements HealthAggregator<MockHealth> {

    @Inject MockPluginDescriptor descriptor;

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override
    public double scoreVantage(VantageBucket<MockHealth> bucket) {
        Map<String, Observation<MockHealth>> freshestByPublisher = new HashMap<>();
        for (Observation<MockHealth> o : bucket.observations()) {
            freshestByPublisher.merge(o.publisher(), o,
                    (a, b) -> a.freshness().isAfter(b.freshness()) ? a : b);
        }
        double sum = 0;
        int n = 0;
        for (Observation<MockHealth> o : freshestByPublisher.values()) {
            switch (o.state()) {
                case FAST -> { sum += 1.0; n++; }
                case DEGRADED -> { sum += 0.5; n++; }
                case DEAD -> { n++; }              // adds 0.0
                case UNKNOWN -> { /* no signal */ }
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }
}
