package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlRemoteHealth;
import com.tb.nw.spi.api.ClusterType;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.ObservationView;
import com.tb.nw.spi.api.SelectionContext;
import com.tb.nw.spi.api.Vantage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlCandidateSelectorTest {

    private final MysqlCandidateSelector selector = build();

    private static MysqlCandidateSelector build() {
        MysqlCandidateSelector s = new MysqlCandidateSelector();
        s.descriptor = new MysqlPluginDescriptor();
        return s;
    }

    /** Fresh LOCAL_SELF confession with controllable replica state + lag. */
    private static ObservationView<MySqlRemoteHealth> slave(
            String node, HealthState state, Boolean io, Boolean sql, Long lag) {
        MySqlRemoteHealth h = MySqlRemoteHealth.ofLocalProbe(
                state, node, 1_000_000L, true, io, sql, lag,
                null, null, 3600, 0, null);
        Observation<MySqlRemoteHealth> o = new Observation<>(
                "c1", ClusterType.MYSQL_MASTER_SLAVE, node, node, "mysql.local",
                Vantage.LOCAL_SELF, state, 1_000_000L, h.timestamp(),
                MysqlPluginDescriptor.PLUGIN_ID, MysqlPluginDescriptor.PLUGIN_VERSION, h);
        return new ObservationView<>(node, List.of(o));
    }

    private SelectionContext ctx() {
        return new SelectionContext("c1", ClusterType.MYSQL_MASTER_SLAVE, Map.of(), List.of());
    }

    @Test void picksTheLeastLaggedHealthySlave() {
        var pick = selector.select(List.of(
                slave("s1", HealthState.FAST, true, true, 8L),
                slave("s2", HealthState.FAST, true, true, 3L)), ctx());
        assertEquals("s2", pick.orElseThrow().value());
    }

    @Test void rejectsStoppedReplicaThreads() {
        var pick = selector.select(List.of(
                slave("s1", HealthState.FAST, false, true, 0L),
                slave("s2", HealthState.FAST, true, true, 5L)), ctx());
        assertEquals("s2", pick.orElseThrow().value());
    }

    @Test void rejectsDeadCandidates() {
        var pick = selector.select(List.of(
                slave("s1", HealthState.DEAD, true, true, 0L)), ctx());
        assertTrue(pick.isEmpty(), "an empty pool is a refusal, never a guess");
    }

    @Test void unknownLagIsNotPromotable() {
        // null lag = the slave can't prove it's caught up -> not a candidate; the caught-up slave wins
        var pick = selector.select(List.of(
                slave("s1", HealthState.FAST, true, true, null),
                slave("s2", HealthState.FAST, true, true, 5L)), ctx());
        assertEquals("s2", pick.orElseThrow().value());
    }

    @Test void rejectsSlaveLaggingBeyondCeiling() {
        // lacks in binlog beyond the promotion window -> promoting it would lose writes
        var pick = selector.select(List.of(
                slave("s1", HealthState.FAST, true, true, 999L)), ctx());
        assertTrue(pick.isEmpty());
    }

    @Test void tieBreaksByNodeName() {
        var pick = selector.select(List.of(
                slave("s2", HealthState.FAST, true, true, 5L),
                slave("s1", HealthState.FAST, true, true, 5L)), ctx());
        assertEquals("s1", pick.orElseThrow().value());
    }

    @Test void noCandidates_refuses() {
        assertTrue(selector.select(List.of(), ctx()).isEmpty());
    }
}
