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
import java.util.Set;

/**
 * Local-vantage probe that runs only when this node is the MySQL master.
 * Reports SELECT 1 latency, server uptime, connected-replica count.
 */
@ApplicationScoped
public class MySqlLocalHealthCheckerMaster implements HealthCheck {

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;

    @Override public String id() { return "mysql.local.master"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }
    @Override public Set<String> requiredFacets() { return Set.of("mysql-master-here"); }

    @Override public HealthReport probe(ProbeContext ctx) {
        Instant start = Instant.now();
        try (Connection c = conns.openLocal()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("canary_query_ok", canaryQueryOk(c));
            detail.put("uptime_seconds", queryLong(c, "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='UPTIME'"));
            detail.put("connected_replicas", countReplicas(c));
            detail.put("read_only", queryInt(c, "SELECT @@read_only"));
            Duration latency = Duration.between(start, Instant.now());
            return MysqlClassifier.classifyByLatency(latency, detail, cfg);
        } catch (Exception e) {
            Duration latency = Duration.between(start, Instant.now());
            return HealthReport.dead(latency, Map.of("error", e.getMessage()));
        }
    }

    private boolean canaryQueryOk(Connection c) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private long queryLong(Connection c, String sql) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private int queryInt(Connection c, String sql) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private int countReplicas(Connection c) {
        Integer n = tryCount(c, "SHOW REPLICAS");          // MySQL 8.0+
        if (n != null) return n;
        n = tryCount(c, "SHOW SLAVE HOSTS");                // MySQL 5.7
        return n == null ? -1 : n;
    }

    private Integer tryCount(Connection c, String sql) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            int n = 0;
            while (rs.next()) n++;
            return n;
        } catch (Exception e) {
            return null;
        }
    }
}
