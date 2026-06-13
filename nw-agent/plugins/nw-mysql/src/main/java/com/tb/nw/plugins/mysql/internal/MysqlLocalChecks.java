package com.tb.nw.plugins.mysql.internal;

import com.tb.nw.spi.api.CheckResult;
import com.tb.nw.spi.api.HealthChecks;

/**
 * The check-set for the LOCAL_SELF probe — Roles 1 and 2, expressed as one
 * method per check and folded worst-wins. Pure: the readings (gathered by the
 * probe from systemctl, df, and SQL) come in, a verdict comes out, so the whole
 * grading is unit-testable without a live MySQL.
 *
 * <ul>
 *   <li><b>Critical (down on fail):</b> systemctl active · data disk has room —
 *       if the service is inactive or the disk is full the node is DOWN even
 *       though the socket may still answer.</li>
 *   <li><b>Role 1 master (degrade on fail):</b> read-write — a master gone
 *       read-only is suspect.</li>
 *   <li><b>Role 2 slave (degrade on fail):</b> replica IO running · replica SQL
 *       running · lag within the promotion window ("does not lack in binlog").
 *       All three passing is the slave's "I can assume master" state.</li>
 * </ul>
 */
public final class MysqlLocalChecks {

    private MysqlLocalChecks() {}

    public static HealthChecks.Verdict evaluate(
            boolean serviceActive, boolean diskHasSpace,
            Boolean readOnly, Boolean replicaIo, Boolean replicaSql, Long secondsBehind,
            long maxPromotionLagSec) {

        boolean slave = replicaIo != null || replicaSql != null || secondsBehind != null;

        HealthChecks.Builder checks = HealthChecks.builder()
                .check("systemctl", () -> serviceActive ? CheckResult.up() : CheckResult.down("mysqld service inactive"))
                .check("disk", () -> diskHasSpace ? CheckResult.up() : CheckResult.down("data disk full"));

        if (slave) {
            checks.check("replica-io", () -> Boolean.TRUE.equals(replicaIo)
                            ? CheckResult.up() : CheckResult.degraded("replica IO thread stopped"))
                  .check("replica-sql", () -> Boolean.TRUE.equals(replicaSql)
                            ? CheckResult.up() : CheckResult.degraded("replica SQL thread stopped"))
                  .check("lag", () -> secondsBehind != null && secondsBehind <= maxPromotionLagSec
                            ? CheckResult.up()
                            : CheckResult.degraded("replica lag " + secondsBehind + "s exceeds "
                                    + maxPromotionLagSec + "s window"));
        } else {
            checks.check("read-write", () -> !Boolean.TRUE.equals(readOnly)
                    ? CheckResult.up() : CheckResult.degraded("master is read-only"));
        }
        return checks.run();
    }
}
