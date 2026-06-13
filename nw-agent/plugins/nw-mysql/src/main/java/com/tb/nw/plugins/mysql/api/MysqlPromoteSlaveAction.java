package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.dependencies.MysqlConnections;
import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlCommandResult;
import com.tb.nw.plugins.mysql.publishes.MySqlPromoteSlaveCommand;
import com.tb.nw.spi.api.ActionContext;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.FailoverAction;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Move 3 — promote the local replica to master. If the command names a
 * required GTID position, wait (bounded by the action deadline) until the
 * server has replayed past it — fail with "gtid-behind" rather than lose
 * data silently. Then: stop replica threads, erase replica config, open the
 * floodgates (read-only off).
 *
 * <p>Idempotent: an already-promoted server (writable, no replica status)
 * returns ok("already-master") without touching anything.</p>
 */
@ApplicationScoped
public class MysqlPromoteSlaveAction implements FailoverAction<MySqlPromoteSlaveCommand> {

    @Inject MysqlConnections conns;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.promote-self"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlPromoteSlaveCommand> commandType() { return MySqlPromoteSlaveCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }

    @Override public Optional<CommandResultEvent> checkIdempotent(ActionContext<MySqlPromoteSlaveCommand> ctx) {
        return Optional.empty();
    }

    @Override public CommandResultEvent execute(ActionContext<MySqlPromoteSlaveCommand> ctx) {
        MySqlPromoteSlaveCommand cmd = ctx.command();
        try (Connection c = conns.openLocal()) {

            MysqlReplicaSql.ReplicaStatus replica = MysqlReplicaSql.replicaStatus(c);
            if (replica == null && !MysqlReplicaSql.readOnly(c)) {
                return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(), Map.of(
                        "already", "master",
                        "gtid_executed", MysqlReplicaSql.executedGtid(c)));
            }

            if (cmd.requiredGtidOrNull() != null && !cmd.requiredGtidOrNull().isBlank()) {
                int waitSec = (int) Math.max(1, ctx.deadline().toSeconds() - 2);
                boolean caughtUp = MysqlReplicaSql.waitForGtid(c, cmd.requiredGtidOrNull(), waitSec);
                if (!caughtUp) {
                    return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(),
                            "gtid-behind: not caught up to " + cmd.requiredGtidOrNull()
                                    + " within " + waitSec + "s");
                }
            }

            MysqlReplicaSql.stopReplica(c);
            MysqlReplicaSql.resetReplicaAll(c);
            MysqlReplicaSql.setReadOnly(c, false);

            return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(), Map.of(
                    "gtid_executed", MysqlReplicaSql.executedGtid(c),
                    "was_replica", String.valueOf(replica != null)));
        } catch (Exception e) {
            return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(), e.getMessage());
        }
    }
}
