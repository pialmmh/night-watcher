package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.dependencies.MysqlConfig;
import com.tb.nw.plugins.mysql.dependencies.MysqlConnections;
import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlRemoteHealth;
import com.tb.nw.spi.api.HealthCheck;
import com.tb.nw.spi.api.HealthReport;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.ProbeContext;
import com.tb.nw.spi.api.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * The application impersonator — the CLIENT-vantage MySQL investigator. Runs
 * on hosts carrying the {@code mysql-client-here} facet (an app-tier VM with
 * the JDBC driver) and answers ONE question: can a client connect to the
 * cluster's master address and run the configured canary query?
 *
 * <h2>Grading contract</h2>
 * <ul>
 *   <li>connect / query throws, or the query exceeds dead-threshold-ms
 *       (enforced as the statement timeout) → {@code DEAD}</li>
 *   <li>query returns zero rows → {@code DEGRADED}</li>
 *   <li>latency ≤ fast-threshold-ms → {@code FAST}</li>
 *   <li>latency ≤ dead-threshold-ms → {@code DEGRADED} (slow but alive —
 *       the degraded threshold marks where FAST ends, the dead threshold
 *       where answering stops counting at all)</li>
 * </ul>
 *
 * <p>After a successful canary the probe enriches the event best-effort over
 * the same connection: {@code read_only} flag (a "master" answering with
 * read_only=1 is the early split-brain tell), server uptime, connected
 * replica count. Enrichment failures never fail the probe.</p>
 *
 * <p>Three consecutive {@code DEAD} verdicts trigger the FailoverCoordinator
 * (see {@code nw-core/coord}).</p>
 */
@ApplicationScoped
public class MySqlRemoteClient implements HealthCheck<MySqlRemoteHealth> {

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.remote.client"; }
    @Override public Vantage vantage() { return Vantage.CLIENT; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlRemoteHealth> eventType() { return MySqlRemoteHealth.class; }
    @Override public Set<String> requiredFacets() { return Set.of("mysql-client-here"); }

    @Override public String target(ProbeContext ctx) {
        return cfg.clientTargetHost().or(cfg::remoteHost).orElse(null);
    }

    @Override public HealthReport<MySqlRemoteHealth> probe(ProbeContext ctx) {
        String host = target(ctx);
        String query = cfg.customQuery();
        if (host == null) {
            return HealthReport.of(
                    MySqlRemoteHealth.noTargetConfigured("unknown", "client target host not configured"),
                    Duration.ZERO);
        }

        Instant start = Instant.now();
        try (Connection c = conns.openClientTarget()) {
            Canary canary = runCanary(c, query, queryTimeoutSec(ctx, start));
            Enrichment extra = enrichBestEffort(c);
            HealthState state = classify(canary.latency(), canary.hasRow());

            MySqlRemoteHealth event = MySqlRemoteHealth.ofClientProbe(
                    state, host,
                    host, query,
                    true, canary.latency().toNanos(), canary.hasRow(), null,
                    extra.readOnly(), extra.uptimeSeconds(), extra.connectedReplicas());
            return HealthReport.of(event, canary.latency());

        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            MySqlRemoteHealth event = MySqlRemoteHealth.ofClientProbe(
                    HealthState.DEAD, host,
                    host, query,
                    false, latency.toNanos(), false, e.getMessage(),
                    null, 0L, 0L);
            return HealthReport.of(event, latency);
        }
    }

    // ── the canary ──

    private record Canary(Duration latency, boolean hasRow) {}

    private Canary runCanary(Connection c, String query, int timeoutSec) throws Exception {
        Instant start = Instant.now();
        try (Statement s = c.createStatement()) {
            s.setQueryTimeout(timeoutSec);
            try (ResultSet rs = s.executeQuery(query)) {
                boolean hasRow = rs.next();
                return new Canary(Duration.between(start, Instant.now()), hasRow);
            }
        }
    }

    /**
     * The statement timeout honors all three bounds: the runner's
     * {@code ctx.deadline()}, the configured query timeout, and
     * dead-threshold-ms — a query still unanswered at the dead threshold IS
     * the DEAD verdict; never wait longer for it.
     */
    private int queryTimeoutSec(ProbeContext ctx, Instant start) {
        long remainingSec = Duration.between(Instant.now(), start.plus(ctx.deadline())).toSeconds();
        long deadSec = Math.max(1, cfg.deadThresholdMs() / 1000);
        long bound = Math.min(Math.min(Math.max(1, remainingSec), cfg.queryTimeoutSec()), deadSec);
        return (int) Math.max(1, bound);
    }

    /** Latency + row-presence → the grading contract in the class javadoc. */
    private HealthState classify(Duration latency, boolean hasRow) {
        if (!hasRow) return HealthState.DEGRADED;
        long ms = latency.toMillis();
        if (ms <= cfg.fastThresholdMs()) return HealthState.FAST;
        if (ms <= cfg.deadThresholdMs()) return HealthState.DEGRADED;
        return HealthState.DEAD;
    }

    // ── best-effort enrichment over the same client connection ──

    private record Enrichment(Boolean readOnly, long uptimeSeconds, long connectedReplicas) {}

    private Enrichment enrichBestEffort(Connection c) {
        return new Enrichment(readReadOnly(c), readUptime(c), countBinlogDumpThreads(c));
    }

    private Boolean readReadOnly(Connection c) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT @@global.read_only")) {
            rs.next();
            return rs.getBoolean(1);
        } catch (Exception e) {
            return null;          // low-privilege canary user — enrichment is optional
        }
    }

    private long readUptime(Connection c) {
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
}
