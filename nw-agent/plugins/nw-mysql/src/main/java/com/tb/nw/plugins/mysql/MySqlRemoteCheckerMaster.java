package com.tb.nw.plugins.mysql;

import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.ProbeContext;
import com.tb.nw.spi.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Remote-vantage probe of a configured MySQL master. Runs from any node that
 * is NOT the master itself. Measures latency from the prober's network path
 * — catches storage-tier issues invisible to local-self probes.
 */
@ApplicationScoped
public class MySqlRemoteCheckerMaster implements HealthCheck {

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;

    @Override public String id() { return "mysql.remote.master"; }
    @Override public Vantage vantage() { return Vantage.REMOTE_PEER; }

    /**
     * No required facets — runs on every node. (When the activation engine
     * lands, the predicate "a master is elected somewhere AND I am not it"
     * will replace this. For now we self-skip if remote-host is unset.)
     */

    @Override public HealthReport probe(ProbeContext ctx) {
        if (cfg.remoteHost().isEmpty()) {
            return HealthReport.unknown("nw.mysql.remote-host not configured");
        }
        Instant start = Instant.now();
        try (Connection c = conns.openRemote();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            boolean ok = rs.next() && rs.getInt(1) == 1;
            Duration latency = Duration.between(start, Instant.now());
            Map<String, Object> detail = new HashMap<>();
            detail.put("target_host", cfg.remoteHost().get());
            detail.put("query_ok", ok);
            return MysqlClassifier.classifyByLatency(latency, detail, cfg);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return HealthReport.dead(latency, Map.of(
                    "target_host", cfg.remoteHost().get(),
                    "error", e.getMessage()));
        }
    }
}
