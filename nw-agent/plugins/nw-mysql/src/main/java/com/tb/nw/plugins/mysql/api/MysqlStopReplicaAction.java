package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.dependencies.MysqlConnections;
import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlCommandResult;
import com.tb.nw.plugins.mysql.publishes.MySqlStopReplicaCommand;
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
 * Move 5 — stop the local replica threads; with {@code resetAll} also erase
 * the replica configuration so the box forgets its old source. Idempotent:
 * stopping a stopped replica is a no-op success.
 */
@ApplicationScoped
public class MysqlStopReplicaAction implements FailoverAction<MySqlStopReplicaCommand> {

    @Inject MysqlConnections conns;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.stop-replica"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlStopReplicaCommand> commandType() { return MySqlStopReplicaCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }

    @Override public Optional<CommandResultEvent> checkIdempotent(ActionContext<MySqlStopReplicaCommand> ctx) {
        return Optional.empty();
    }

    @Override public CommandResultEvent execute(ActionContext<MySqlStopReplicaCommand> ctx) {
        MySqlStopReplicaCommand cmd = ctx.command();
        try (Connection c = conns.openLocal()) {
            MysqlReplicaSql.stopReplica(c);
            if (cmd.resetAll()) MysqlReplicaSql.resetReplicaAll(c);
            return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(),
                    Map.of("reset_all", String.valueOf(cmd.resetAll())));
        } catch (Exception e) {
            return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(), e.getMessage());
        }
    }
}
