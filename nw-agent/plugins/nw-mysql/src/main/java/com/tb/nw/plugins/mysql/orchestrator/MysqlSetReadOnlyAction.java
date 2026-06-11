package com.tb.nw.plugins.mysql.orchestrator;

import com.tb.nw.plugins.mysql.MysqlConnections;
import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.events.MySqlCommandResult;
import com.tb.nw.plugins.mysql.events.MySqlSetReadOnlyCommand;
import com.tb.nw.spi.ActionContext;
import com.tb.nw.spi.CommandResultEvent;
import com.tb.nw.spi.FailoverAction;
import com.tb.nw.spi.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Move 1 — flip the global read-only flag on the local server. Harmless and
 * reversible: the move that proves the whole dispatch pipe end-to-end.
 * Idempotent: already at the desired value ⇒ ok without touching anything.
 */
@ApplicationScoped
public class MysqlSetReadOnlyAction implements FailoverAction<MySqlSetReadOnlyCommand> {

    @Inject MysqlConnections conns;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.set-read-only"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlSetReadOnlyCommand> commandType() { return MySqlSetReadOnlyCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }   // any role — see roles gate note in spec

    @Override public Optional<CommandResultEvent> checkIdempotent(ActionContext<MySqlSetReadOnlyCommand> ctx) {
        return Optional.empty();   // replay memory lives in the endpoint; the SQL itself re-checks
    }

    @Override public CommandResultEvent execute(ActionContext<MySqlSetReadOnlyCommand> ctx) {
        MySqlSetReadOnlyCommand cmd = ctx.command();
        try (Connection c = conns.openLocal()) {
            boolean current = MysqlReplicaSql.readOnly(c);
            if (current == cmd.readOnly()) {
                return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(),
                        Map.of("read_only", String.valueOf(current), "changed", "false"));
            }
            MysqlReplicaSql.setReadOnly(c, cmd.readOnly());
            return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(),
                    Map.of("read_only", String.valueOf(cmd.readOnly()), "changed", "true"));
        } catch (Exception e) {
            return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(), e.getMessage());
        }
    }
}
