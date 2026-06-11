package com.tb.nw.testkit;

import com.tb.nw.spi.ClusterType;
import com.tb.nw.spi.HealthAggregator;
import com.tb.nw.spi.HealthCheckEvent;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.NwEvent;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.Vantage;
import com.tb.nw.spi.VantageBucket;

import java.time.Instant;

/** A minimal complete plugin for core tests: descriptor + health event + aggregator. */
public final class TestPlugin {
    private TestPlugin() {}

    public static final String ID = "nw-test";
    public static final String VERSION = "9.9.9";

    public record TestHealth(
            String eventId, Instant timestamp,
            String pluginId, String pluginVersion,
            HealthState healthState, String target
    ) implements HealthCheckEvent {
        public static TestHealth of(HealthState state, String target) {
            return new TestHealth(NwEvent.newEventId(), Instant.now(), ID, VERSION, state, target);
        }
    }

    public static PluginDescriptor descriptor() {
        return new PluginDescriptor() {
            @Override public String pluginId() { return ID; }
            @Override public String pluginVersion() { return VERSION; }
            @Override public String serviceType() { return "test"; }
            @Override public Class<? extends HealthCheckEvent> healthEventType() { return TestHealth.class; }
            @Override public java.util.Set<Class<? extends com.tb.nw.spi.CommandEvent>> commandEventTypes() {
                return java.util.Set.of();
            }
        };
    }

    /** FAST=1.0 · DEGRADED=0.5 · DEAD=0.0 · UNKNOWN=skip, averaged — mirrors the mock plugin. */
    public static HealthAggregator<TestHealth> aggregator() {
        return new HealthAggregator<>() {
            @Override public PluginDescriptor descriptor() { return TestPlugin.descriptor(); }
            @Override public double scoreVantage(VantageBucket<TestHealth> bucket) {
                double sum = 0; int n = 0;
                for (Observation<TestHealth> o : bucket.observations()) {
                    switch (o.state()) {
                        case FAST -> { sum += 1.0; n++; }
                        case DEGRADED -> { sum += 0.5; n++; }
                        case DEAD -> n++;
                        case UNKNOWN -> {}
                    }
                }
                return n == 0 ? Double.NaN : sum / n;
            }
        };
    }

    /** A fully-populated Observation envelope around a TestHealth event. */
    public static Observation<TestHealth> obs(String cluster, String target, String publisher,
                                              String investigator, Vantage vantage, HealthState state) {
        TestHealth ev = TestHealth.of(state, target);
        return new Observation<>(cluster, ClusterType.GENERIC, target, publisher, investigator,
                vantage, state, 1_000_000L, ev.timestamp(), ID, VERSION, ev);
    }
}
