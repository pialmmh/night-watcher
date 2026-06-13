package com.tb.nw.plugins.mysql.api;

import com.tb.nw.plugins.mysql.dependencies.MysqlConfig;
import com.tb.nw.plugins.mysql.dependencies.MysqlConnections;
import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.publishes.MySqlCommandResult;
import com.tb.nw.plugins.mysql.publishes.MySqlStartReplicaCommand;
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
 * Move 4 — point the local server at a new source and start replication.
 * The command carries only a credentials REFERENCE; the actual user/password
 * resolve from this agent's environment (NW_MYSQL_REPL_USER /
 * NW_MYSQL_REPL_PASSWORD via config) — a secret never rides the wire.
 */
@ApplicationScoped
public class MysqlStartReplicaAction implements FailoverAction<MySqlStartReplicaCommand> {

    @Inject MysqlConnections conns;
    @Inject MysqlConfig cfg;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.start-replica"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlStartReplicaCommand> commandType() { return MySqlStartReplicaCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }

    @Override public Optional<CommandResultEvent> checkIdempotent(ActionContext<MySqlStartReplicaCommand> ctx) {
        return Optional.empty();
    }

    @Override public CommandResultEvent execute(ActionContext<MySqlStartReplicaCommand> ctx) {
        MySqlStartReplicaCommand cmd = ctx.command();

        Optional<String> user = cfg.replicationUser();
        Optional<String> password = cfg.replicationPassword();
        if (user.isEmpty() || password.isEmpty()) {
            return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(),
                    "replication credentials unresolved (ref=" + cmd.replicationCredentialsRef()
                            + ") — set NW_MYSQL_REPL_USER / NW_MYSQL_REPL_PASSWORD");
        }

        try (Connection c = conns.openLocal()) {
            MysqlReplicaSql.ReplicaStatus current = MysqlReplicaSql.replicaStatus(c);
            if (current != null && Boolean.TRUE.equals(current.ioRunning())
                    && Boolean.TRUE.equals(current.sqlRunning())) {
                return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(),
                        Map.of("already", "replicating"));
            }
            MysqlReplicaSql.stopReplica(c);   // safe when stopped — idempotent precursor
            MysqlReplicaSql.changeSource(c, cmd.sourceHost(), cmd.sourcePort(), user.get(), password.get());
            MysqlReplicaSql.startReplica(c);
            return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(), Map.of(
                    "source", cmd.sourceHost() + ":" + cmd.sourcePort()));
        } catch (Exception e) {
            return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(), e.getMessage());
        }
    }
}
