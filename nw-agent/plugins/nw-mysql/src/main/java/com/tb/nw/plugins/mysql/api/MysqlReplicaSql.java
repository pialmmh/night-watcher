package com.tb.nw.plugins.mysql.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Replication SQL across server generations. MySQL 8.0.22+ speaks
 * REPLICA / REPLICATION SOURCE; Percona / MySQL 5.7 (dell-sms-master) speaks
 * SLAVE / MASTER. Every helper tries the modern wording first and falls back.
 *
 * <p>Static utility — actions pass their own {@link Connection}; no state.</p>
 */
final class MysqlReplicaSql {

    private MysqlReplicaSql() {}

    /** Normalized view of SHOW REPLICA/SLAVE STATUS. Null when not a replica. */
    record ReplicaStatus(
            Boolean ioRunning,
            Boolean sqlRunning,
            Long secondsBehind,
            String lastIoError,
            String lastSqlError
    ) {}

    static void stopReplica(Connection c) throws SQLException {
        execEither(c, "STOP REPLICA", "STOP SLAVE");
    }

    static void startReplica(Connection c) throws SQLException {
        execEither(c, "START REPLICA", "START SLAVE");
    }

    static void resetReplicaAll(Connection c) throws SQLException {
        execEither(c, "RESET REPLICA ALL", "RESET SLAVE ALL");
    }

    /** Re-point this server at a new source. Credentials are literals — quote-escaped. */
    static void changeSource(Connection c, String host, int port, String user, String password)
            throws SQLException {
        String modern = "CHANGE REPLICATION SOURCE TO SOURCE_HOST=" + q(host)
                + ", SOURCE_PORT=" + port
                + ", SOURCE_USER=" + q(user)
                + ", SOURCE_PASSWORD=" + q(password)
                + ", SOURCE_AUTO_POSITION=1";
        String legacy = "CHANGE MASTER TO MASTER_HOST=" + q(host)
                + ", MASTER_PORT=" + port
                + ", MASTER_USER=" + q(user)
                + ", MASTER_PASSWORD=" + q(password)
                + ", MASTER_AUTO_POSITION=1";
        execEither(c, modern, legacy);
    }

    /** SHOW REPLICA/SLAVE STATUS normalized across column name generations. */
    static ReplicaStatus replicaStatus(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = showReplicaStatus(s)) {
            if (!rs.next()) return null;
            return new ReplicaStatus(
                    yesNo(col(rs, "Replica_IO_Running", "Slave_IO_Running")),
                    yesNo(col(rs, "Replica_SQL_Running", "Slave_SQL_Running")),
                    asLong(col(rs, "Seconds_Behind_Source", "Seconds_Behind_Master")),
                    col(rs, "Last_IO_Error", "Last_IO_Error"),
                    col(rs, "Last_SQL_Error", "Last_SQL_Error"));
        }
    }

    private static ResultSet showReplicaStatus(Statement s) throws SQLException {
        try {
            return s.executeQuery("SHOW REPLICA STATUS");
        } catch (SQLException e) {
            return s.executeQuery("SHOW SLAVE STATUS");
        }
    }

    static boolean readOnly(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT @@global.read_only")) {
            rs.next();
            return rs.getBoolean(1);
        }
    }

    /** Sets read_only; tries super_read_only too (absent on some builds — ignored). */
    static void setReadOnly(Connection c, boolean value) throws SQLException {
        String v = value ? "1" : "0";
        try (Statement s = c.createStatement()) {
            try {
                s.execute("SET GLOBAL super_read_only = " + v);
            } catch (SQLException ignored) {
                // server has no super_read_only — fine
            }
            s.execute("SET GLOBAL read_only = " + v);
        }
    }

    /** This server's executed GTID set ("" when GTIDs are off). */
    static String executedGtid(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT @@global.gtid_executed")) {
            rs.next();
            String g = rs.getString(1);
            return g == null ? "" : g;
        }
    }

    /** Wait until this server has replayed past {@code gtidSet}. True when reached in time. */
    static boolean waitForGtid(Connection c, String gtidSet, int timeoutSec) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT WAIT_FOR_EXECUTED_GTID_SET(" + q(gtidSet) + ", " + timeoutSec + ")")) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    /** Kills every client connection except system threads and ourselves. Returns the kill count. */
    static int killClients(Connection c) throws SQLException {
        int killed = 0;
        try (Statement list = c.createStatement();
             ResultSet rs = list.executeQuery(
                     "SELECT id, user, command FROM information_schema.processlist"
                             + " WHERE id <> CONNECTION_ID()")) {
            try (Statement kill = c.createStatement()) {
                while (rs.next()) {
                    String user = rs.getString("user");
                    String command = rs.getString("command");
                    if (isSystem(user, command)) continue;
                    try {
                        kill.execute("KILL " + rs.getLong("id"));
                        killed++;
                    } catch (SQLException ignored) {
                        // connection raced away — already gone is the goal
                    }
                }
            }
        }
        return killed;
    }

    // ── helpers ──

    private static boolean isSystem(String user, String command) {
        if (user == null) return true;
        if ("system user".equalsIgnoreCase(user) || "event_scheduler".equalsIgnoreCase(user)) return true;
        // replication dump threads serve slaves — killing them is stop-replica's job, not fencing's
        return command != null && command.startsWith("Binlog Dump");
    }

    private static void execEither(Connection c, String modern, String legacy) throws SQLException {
        try (Statement s = c.createStatement()) {
            try {
                s.execute(modern);
            } catch (SQLException e) {
                s.execute(legacy);
            }
        }
    }

    private static String col(ResultSet rs, String modern, String legacy) {
        try {
            return rs.getString(modern);
        } catch (SQLException e) {
            try {
                return rs.getString(legacy);
            } catch (SQLException e2) {
                return null;
            }
        }
    }

    private static Boolean yesNo(String v) {
        return v == null ? null : "Yes".equalsIgnoreCase(v);
    }

    private static Long asLong(String v) {
        try {
            return v == null ? null : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Single-quote escape for statements that reject bind parameters. */
    private static String q(String v) {
        return "'" + (v == null ? "" : v.replace("'", "''")) + "'";
    }
}
