package com.tb.nw.plugins.mysql.orchestrator;

import com.tb.nw.plugins.mysql.MysqlConnections;
import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.plugins.mysql.events.MySqlCommandResult;
import com.tb.nw.plugins.mysql.events.MySqlFenceMasterCommand;
import com.tb.nw.spi.api.ActionContext;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.FailoverAction;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Move 2 — fence the local (ex-)master: read-only + super-read-only, then
 * kill every client connection except system threads and ourselves. Writers
 * are gone mid-statement — that is the point.
 *
 * <p>The "drop the VIP" half is acknowledged but deferred (no VIP in the
 * test environment); the result notes it as skipped.</p>
 */
@ApplicationScoped
public class MysqlFenceMasterAction implements FailoverAction<MySqlFenceMasterCommand> {

    private static final Logger LOG = Logger.getLogger(MysqlFenceMasterAction.class);

    @Inject MysqlConnections conns;
    @Inject MysqlPluginDescriptor descriptor;

    @Override public String id() { return "mysql.fence-self"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MySqlFenceMasterCommand> commandType() { return MySqlFenceMasterCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }

    @Override public Optional<CommandResultEvent> checkIdempotent(ActionContext<MySqlFenceMasterCommand> ctx) {
        return Optional.empty();
    }

    @Override public CommandResultEvent execute(ActionContext<MySqlFenceMasterCommand> ctx) {
        MySqlFenceMasterCommand cmd = ctx.command();
        try (Connection c = conns.openLocal()) {
            boolean alreadyReadOnly = MysqlReplicaSql.readOnly(c);
            MysqlReplicaSql.setReadOnly(c, true);          // re-applying is harmless — idempotent
            int killed = MysqlReplicaSql.killClients(c);   // re-fencing kills any stragglers

            if (cmd.dropVip()) {
                LOG.warn("fence-master: dropVip requested but VIP handling is not implemented in this cut — skipped");
            }
            return MySqlCommandResult.ok(cmd.eventId(), cmd.commandKind(), Map.of(
                    "was_read_only", String.valueOf(alreadyReadOnly),
                    "killed_connections", String.valueOf(killed),
                    "vip", cmd.dropVip() ? "skipped-not-implemented" : "not-requested"));
        } catch (Exception e) {
            return MySqlCommandResult.failure(cmd.eventId(), cmd.commandKind(), e.getMessage());
        }
    }
}
