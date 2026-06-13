package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlRemoteHealth;
import com.tb.nw.spi.api.HealthAggregator;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.VantageBucket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * Scores one (target, vantage) bucket for the Resolver — the piece that lets
 * a MySQL cluster vote. Without it the Resolver had no opinion on mysql
 * targets and {@code require-odown} would hold forever.
 *
 * <p>Pure function (shadow determinism): freshest observation per publisher,
 * graded FAST=1.0 · DEGRADED=0.5 · DEAD=0.0 (UNKNOWN = no signal), averaged.
 * The probes already encode the mysql-specific judgment (latency thresholds,
 * replication state) into the HealthState grade — the aggregator deliberately
 * re-judges nothing.</p>
 */
@ApplicationScoped
public class MysqlHealthAggregator implements HealthAggregator<MySqlRemoteHealth> {

    @Inject MysqlPluginDescriptor descriptor;

    @Override public PluginDescriptor descriptor() { return descriptor; }

    @Override
    public double scoreVantage(VantageBucket<MySqlRemoteHealth> bucket) {
        Map<String, Observation<MySqlRemoteHealth>> freshestByPublisher = new HashMap<>();
        for (Observation<MySqlRemoteHealth> o : bucket.observations()) {
            freshestByPublisher.merge(o.publisher(), o,
                    (a, b) -> a.freshness().isAfter(b.freshness()) ? a : b);
        }
        double sum = 0;
        int n = 0;
        for (Observation<MySqlRemoteHealth> o : freshestByPublisher.values()) {
            switch (o.state()) {
                case FAST -> { sum += 1.0; n++; }
                case DEGRADED -> { sum += 0.5; n++; }
                case DEAD -> { n++; }
                case UNKNOWN -> { /* no signal */ }
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }
}
