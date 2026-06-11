package com.tb.nw.plugins.mysql.orchestrator;

import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.events.MySqlRemoteHealth;
import com.tb.nw.spi.ClusterType;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.Vantage;
import com.tb.nw.spi.VantageBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlHealthAggregatorTest {

    private final MysqlHealthAggregator agg = build();

    private static MysqlHealthAggregator build() {
        MysqlHealthAggregator a = new MysqlHealthAggregator();
        a.descriptor = new MysqlPluginDescriptor();
        return a;
    }

    private static Observation<MySqlRemoteHealth> canary(String publisher, HealthState state, Instant when) {
        MySqlRemoteHealth h = MySqlRemoteHealth.ofClientProbe(
                state, "master-1", "10.0.0.1", "SHOW DATABASES",
                state != HealthState.DEAD, 1_000_000L, true, null,
                false, 3600, 2);
        return new Observation<>("c1", ClusterType.MYSQL_MASTER_SLAVE, "master-1", publisher,
                "mysql.remote.client", Vantage.CLIENT, state, 1_000_000L, when,
                MysqlPluginDescriptor.PLUGIN_ID, MysqlPluginDescriptor.PLUGIN_VERSION, h);
    }

    private static VantageBucket<MySqlRemoteHealth> bucket(Observation<MySqlRemoteHealth>... obs) {
        return new VantageBucket<>("master-1", Vantage.CLIENT, List.of(obs));
    }

    @Test void gradesMapToScores() {
        Instant now = Instant.now();
        assertEquals(1.0, agg.scoreVantage(bucket(canary("app1", HealthState.FAST, now))));
        assertEquals(0.5, agg.scoreVantage(bucket(canary("app1", HealthState.DEGRADED, now))));
        assertEquals(0.0, agg.scoreVantage(bucket(canary("app1", HealthState.DEAD, now))));
    }

    @Test void freshestPerPublisherWins() {
        assertEquals(0.0, agg.scoreVantage(bucket(
                canary("app1", HealthState.FAST, Instant.now().minusSeconds(20)),
                canary("app1", HealthState.DEAD, Instant.now()))));
    }

    @Test void multiplePublishersAverage() {
        Instant now = Instant.now();
        assertEquals(0.5, agg.scoreVantage(bucket(
                canary("app1", HealthState.FAST, now),
                canary("app2", HealthState.DEAD, now))));
    }

    @Test void unknownOnly_isSilence() {
        assertTrue(Double.isNaN(agg.scoreVantage(
                bucket(canary("app1", HealthState.UNKNOWN, Instant.now())))));
    }

    @Test void emptyBucket_isSilence() {
        assertTrue(Double.isNaN(agg.scoreVantage(bucket())));
    }
}
