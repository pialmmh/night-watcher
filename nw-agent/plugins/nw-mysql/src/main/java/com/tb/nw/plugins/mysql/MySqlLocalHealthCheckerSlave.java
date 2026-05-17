package com.tb.nw.plugins.mysql;

import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.ProbeContext;
import com.tb.nw.spi.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Local-vantage probe that runs only when this node is a MySQL replica.
 * Parses SHOW REPLICA STATUS for IO / SQL thread state, lag, last error.
 */
@ApplicationScoped
public class MySqlLocalHealthCheckerSlave implements HealthCheck {

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;

    @Override public String id() { return "mysql.local.slave"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }
    @Override public Set<String> requiredFacets() { return Set.of("mysql-slave-here"); }

    @Override public HealthReport probe(ProbeContext ctx) {
        Instant start = Instant.now();
        try (Connection c = conns.openLocal()) {
            Map<String, Object> detail = readReplicaStatus(c);
            Duration latency = elapsed(start);
            if (detail.isEmpty()) {
                return HealthReport.degraded(latency,
                        Map.of("error", "no replica status row — replica not configured?"));
            }
            return classify(detail, latency);
        } catch (Exception e) {
            return HealthReport.dead(elapsed(start), Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> readReplicaStatus(Connection c) {
        Map<String, Object> modern = tryReadOneRow(c, "SHOW REPLICA STATUS");       // 8.0+
        return modern.isEmpty() ? tryReadOneRow(c, "SHOW SLAVE STATUS") : modern;    // 5.7
    }

    private Map<String, Object> tryReadOneRow(Connection c, String sql) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (!rs.next()) return Map.of();
            return readRow(rs);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> readRow(ResultSet rs) throws Exception {
        Map<String, Object> out = new HashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            String col = md.getColumnLabel(i);
            if (isInteresting(col)) out.put(col, rs.getString(i));
        }
        return out;
    }

    private boolean isInteresting(String col) {
        // 8.0+ names                                          5.7 names
        return col.equals("Replica_IO_Running")            || col.equals("Slave_IO_Running")
                || col.equals("Replica_SQL_Running")        || col.equals("Slave_SQL_Running")
                || col.equals("Seconds_Behind_Source")      || col.equals("Seconds_Behind_Master")
                || col.equals("Last_IO_Error")
                || col.equals("Last_SQL_Error")
                || col.equals("Source_Host")                || col.equals("Master_Host")
                || col.equals("Source_Server_Id")           || col.equals("Master_Server_Id")
                || col.equals("Exec_Source_Log_Pos")        || col.equals("Exec_Master_Log_Pos");
    }

    private HealthReport classify(Map<String, Object> detail, Duration latency) {
        boolean ioRunning  = isYes(detail, "Replica_IO_Running",  "Slave_IO_Running");
        boolean sqlRunning = isYes(detail, "Replica_SQL_Running", "Slave_SQL_Running");
        long lag = lagSeconds(detail);
        if (!ioRunning || !sqlRunning)               return HealthReport.dead(latency, detail);
        if (lag < 0)                                  return HealthReport.degraded(latency, detail);
        if (lag <= 5)                                 return HealthReport.fast(latency, detail);
        if (lag <= 60)                                return HealthReport.degraded(latency, detail);
        return HealthReport.dead(latency, detail);
    }

    private boolean isYes(Map<String, Object> detail, String modern, String legacy) {
        return "Yes".equals(detail.get(modern)) || "Yes".equals(detail.get(legacy));
    }

    private long lagSeconds(Map<String, Object> detail) {
        long modern = parseLong(detail.get("Seconds_Behind_Source"), Long.MIN_VALUE);
        if (modern != Long.MIN_VALUE) return modern;
        return parseLong(detail.get("Seconds_Behind_Master"), -1);
    }

    private long parseLong(Object o, long fallback) {
        try { return o == null ? fallback : Long.parseLong(o.toString()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private Duration elapsed(Instant start) {
        return Duration.between(start, Instant.now());
    }
}
