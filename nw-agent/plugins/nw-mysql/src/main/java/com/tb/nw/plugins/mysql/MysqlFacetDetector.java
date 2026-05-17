package com.tb.nw.plugins.mysql;

import com.tb.nw.spi.FacetDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Detects local MySQL state and publishes the appropriate facets:
 *   mysql-binary-present     — connection succeeds (process is up + auth works)
 *   mysql-running-here       — synonym; kept distinct for future kernel-level checks
 *   mysql-master-here        — @@read_only = 0
 *   mysql-slave-here         — SHOW REPLICA STATUS non-empty
 *
 * A node can be both master-here and slave-here transitionally (e.g. mid-promotion);
 * the activation engine resolves that ambiguity.
 */
@ApplicationScoped
public class MysqlFacetDetector implements FacetDetector {

    private static final Logger LOG = Logger.getLogger(MysqlFacetDetector.class);

    private static final Set<String> DECLARED = Set.of(
            "mysql-binary-present", "mysql-running-here",
            "mysql-master-here", "mysql-slave-here");

    @Inject MysqlConnections conns;

    @Override public Set<String> declaredFacets() { return DECLARED; }

    @Override public Set<String> detect() {
        Set<String> out = new LinkedHashSet<>();
        try (Connection c = conns.openLocal()) {
            out.add("mysql-binary-present");
            out.add("mysql-running-here");
            if (isWriteable(c))     out.add("mysql-master-here");
            if (hasReplicaStatus(c)) out.add("mysql-slave-here");
        } catch (Exception e) {
            LOG.debugf("local mysql probe failed (no facets emitted): %s", e.getMessage());
        }
        return out;
    }

    private boolean isWriteable(Connection c) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT @@read_only")) {
            return rs.next() && rs.getInt(1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasReplicaStatus(Connection c) {
        return tryHasRow(c, "SHOW REPLICA STATUS")          // 8.0+
                || tryHasRow(c, "SHOW SLAVE STATUS");        // 5.7
    }

    private boolean tryHasRow(Connection c, String sql) {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
