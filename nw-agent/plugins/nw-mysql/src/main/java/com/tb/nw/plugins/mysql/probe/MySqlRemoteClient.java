package com.tb.nw.plugins.mysql.probe;

import com.tb.nw.plugins.mysql.MysqlConfig;
import com.tb.nw.plugins.mysql.MysqlConnections;
import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.entities.MysqlObservationDetail;
import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.ProbeContext;
import com.tb.nw.spi.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * The only MySQL investigator in the current cut. Runs on hosts carrying
 * the {@code mysql-client-here} facet — typically an application VM with the
 * JDBC driver — and answers a single question: can a client connect to the
 * cluster's master address and run the configured query?
 *
 * <p>Verdict mapping:</p>
 * <ul>
 *   <li>Query throws or connect fails → {@link HealthReport#dead}</li>
 *   <li>Query returns zero rows → {@link HealthReport#degraded}</li>
 *   <li>Query returns ≥1 row + latency ≤ fast threshold → {@link HealthReport#fast}</li>
 *   <li>Query returns rows but latency ≥ degraded / dead thresholds → graded accordingly</li>
 * </ul>
 *
 * <p>Three consecutive {@code DEAD} verdicts trigger the FailoverCoordinator
 * (see {@code nw-core/coord}).</p>
 *
 * <p>Every {@link MysqlObservationDetail} the probe emits is stamped with
 * {@link MysqlPluginDescriptor#PLUGIN_ID} / {@link MysqlPluginDescriptor#PLUGIN_VERSION}.
 * Consumers (FailureTracker, ObservationCache) verify those stamps and drop
 * any envelope whose version doesn't match the loaded plugin.</p>
 */
@ApplicationScoped
public class MySqlRemoteClient implements HealthCheck<MysqlObservationDetail> {

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.remote.client"; }
    @Override public Vantage vantage() { return Vantage.CLIENT; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MysqlObservationDetail> detailType() { return MysqlObservationDetail.class; }
    @Override public Set<String> requiredFacets() { return Set.of("mysql-client-here"); }

    @Override public String target(ProbeContext ctx) {
        return cfg.clientTargetHost().or(cfg::remoteHost).orElse(null);
    }

    @Override public HealthReport<MysqlObservationDetail> probe(ProbeContext ctx) {
        String host = cfg.clientTargetHost().or(cfg::remoteHost).orElse(null);
        String query = cfg.customQuery();

        if (host == null) {
            return HealthReport.unknown(
                    Duration.ZERO,
                    MysqlObservationDetail.noTargetConfigured("client target host not configured"));
        }

        Instant start = Instant.now();
        try (Connection c = conns.openClientTarget();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(query)) {

            boolean hasRow = rs.next();
            Duration latency = Duration.between(start, Instant.now());
            MysqlObservationDetail detail = MysqlObservationDetail.ofClientProbe(
                    host, query, /*canaryOk=*/ true, latency.toNanos(), hasRow, null);

            if (!hasRow) {
                return HealthReport.degraded(latency, detail);
            }
            return classify(latency, detail);

        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return HealthReport.dead(latency,
                    MysqlObservationDetail.ofClientProbe(
                            host, query, /*canaryOk=*/ false, latency.toNanos(), false,
                            e.getMessage()));
        }
    }

    /** Latency → FAST / DEGRADED / DEAD bucketing per the plugin's threshold config. */
    private HealthReport<MysqlObservationDetail> classify(Duration latency, MysqlObservationDetail detail) {
        long ms = latency.toMillis();
        HealthState state = ms <= cfg.fastThresholdMs()
                ? HealthState.FAST
                : (ms <= cfg.degradedThresholdMs() ? HealthState.DEGRADED : HealthState.DEAD);
        return new HealthReport<>(state, latency, detail, Instant.now());
    }
}
