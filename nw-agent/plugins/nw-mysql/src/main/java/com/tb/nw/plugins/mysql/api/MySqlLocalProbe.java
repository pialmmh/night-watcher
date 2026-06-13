package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.dependencies.MysqlConfig;
import com.tb.nw.plugins.mysql.dependencies.MysqlConnections;
import com.tb.nw.plugins.mysql.dependencies.MysqlOsProbe;
import com.tb.nw.plugins.mysql.internal.MysqlLocalChecks;
import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlRemoteHealth;
import com.tb.nw.spi.api.HealthCheck;
import com.tb.nw.spi.api.HealthChecks;
import com.tb.nw.spi.api.HealthReport;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.ProbeContext;
import com.tb.nw.spi.api.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * The local scout — runs on every host that carries a live MySQL
 * ({@code mysql-running-here} facet) and publishes what the database says
 * about ITSELF: read-only flag, replica thread state, lag, uptime. This is
 * the evidence stream the CandidateSelector picks promotion targets from.
 *
 * <p>Target = the local node — these observations are keyed by node name,
 * which is exactly how the roles board and the selector address candidates.</p>
 */
@ApplicationScoped
public class MySqlLocalProbe implements HealthCheck<MySqlRemoteHealth> {

    private static final Logger LOG = Logger.getLogger(MySqlLocalProbe.class);

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;
    @Inject MysqlOsProbe os;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.local"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlRemoteHealth> eventType() { return MySqlRemoteHealth.class; }
    @Override public Set<String> requiredFacets() { return Set.of("mysql-running-here"); }
    // target(ctx) default — the local node. Exactly what we want.

    @Override public HealthReport<MySqlRemoteHealth> probe(ProbeContext ctx) {
        Instant start = Instant.now();
        try (Connection c = conns.openLocal()) {

            boolean readOnly = queryBoolean(c, "SELECT @@global.read_only");
            ReplicaSnapshot replica = replicaSnapshot(c);
            long uptime = queryUptime(c);
            long dumpThreads = countBinlogDumpThreads(c);
            Duration latency = Duration.between(start, Instant.now());

            HealthChecks.Verdict verdict = MysqlLocalChecks.evaluate(
                    os.serviceActive(), os.dataDiskHasSpace(),
                    readOnly, replica.io(), replica.sql(), replica.behind(),
                    cfg.maxPromotionLagSec());
            if (!verdict.up()) LOG.infof("mysql.local %s: %s", verdict.state(), verdict.summary());

            MySqlRemoteHealth event = MySqlRemoteHealth.ofLocalProbe(
                    verdict.state(), ctx.localNode(),
                    latency.toNanos(), readOnly,
                    replica.io(), replica.sql(), replica.behind(),
                    replica.lastIo(), replica.lastSql(),
                    uptime, dumpThreads, null);
            return HealthReport.of(event, latency);

        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            MySqlRemoteHealth event = MySqlRemoteHealth.ofLocalProbe(
                    HealthState.DEAD, ctx.localNode(),
                    latency.toNanos(), null,
                    null, null, null, null, null,
                    0L, 0L, e.getMessage());
            return HealthReport.of(event, latency);
        }
    }

    // ── local status reads ──

    private record ReplicaSnapshot(Boolean io, Boolean sql, Long behind, String lastIo, String lastSql) {}

    private ReplicaSnapshot replicaSnapshot(Connection c) {
        try (Statement s = c.createStatement(); ResultSet rs = showReplicaStatus(s)) {
            if (!rs.next()) return new ReplicaSnapshot(null, null, null, null, null);
            return new ReplicaSnapshot(
                    yes(col(rs, "Replica_IO_Running", "Slave_IO_Running")),
                    yes(col(rs, "Replica_SQL_Running", "Slave_SQL_Running")),
                    parseLong(col(rs, "Seconds_Behind_Source", "Seconds_Behind_Master")),
                    col(rs, "Last_IO_Error", "Last_IO_Error"),
                    col(rs, "Last_SQL_Error", "Last_SQL_Error"));
        } catch (Exception e) {
            return new ReplicaSnapshot(null, null, null, null, null);
        }
    }

    private static ResultSet showReplicaStatus(Statement s) throws Exception {
        try {
            return s.executeQuery("SHOW REPLICA STATUS");
        } catch (Exception e) {
            return s.executeQuery("SHOW SLAVE STATUS");
        }
    }

    private boolean queryBoolean(Connection c, String sql) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    private long queryUptime(Connection c) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SHOW GLOBAL STATUS LIKE 'Uptime'")) {
            return rs.next() ? rs.getLong(2) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long countBinlogDumpThreads(Connection c) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.processlist WHERE command LIKE 'Binlog Dump%'")) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String col(ResultSet rs, String modern, String legacy) {
        try {
            return rs.getString(modern);
        } catch (Exception e) {
            try {
                return rs.getString(legacy);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static Boolean yes(String v) { return v == null ? null : "Yes".equalsIgnoreCase(v); }

    private static Long parseLong(String v) {
        try {
            return v == null ? null : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
