package com.tb.nw.core.door;

import com.tb.nw.core.PluginRegistry;
import com.tb.nw.core.boards.FailoverGate;
import com.tb.nw.core.boards.RoleStore;
import com.tb.nw.core.dispatch.CommandEnvelope;
import com.tb.nw.core.dispatch.ResultEnvelope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tb.nw.core.AgentConfig;
import com.tb.nw.core.CorePluginDescriptor;
import com.tb.nw.core.coordinator.FailoverConfig;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.spi.api.ActionContext;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandRefused;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.FailoverAction;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Every agent's single command door — JSON over the agent's HTTP server
 * (7102, bind policy applies; mTLS gRPC on 7103 is the later hardening).
 *
 * <p>Top level reads as the four gates from the story, in order:
 * identity → address → epoch → replay, then route and reply. The endpoint
 * never runs SQL and never edits a command.</p>
 */
@ApplicationScoped
@Path("/agent")
public class ActionEndpoint {

    private static final Logger LOG = Logger.getLogger(ActionEndpoint.class);

    @Inject AgentConfig cfg;
    @Inject FailoverConfig failoverCfg;
    @Inject PluginRegistry plugins;
    @Inject ActionRegistry actions;
    @Inject IdempotencyStore replays;
    @Inject RoleStore roles;
    @Inject FailoverGate gate;
    @Inject Fabric fabric;
    @Inject ObjectMapper json;

    @POST
    @Path("/command")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultEnvelope command(CommandEnvelope envelope) {
        JsonNode node = envelope.command();
        String inReplyTo = node.path("eventId").asText(null);

        PluginDescriptor plugin = gateIdentity(node);
        if (plugin == null) return refuse(inReplyTo, "version-mismatch");

        CommandEvent cmd = parseWhitelisted(plugin, envelope);
        if (cmd == null) return refuse(inReplyTo, "unknown-command-class");

        String addressProblem = gateAddress(cmd);
        if (addressProblem != null) return refuse(cmd.eventId(), addressProblem);

        String epochProblem = gateEpoch(cmd);
        if (epochProblem != null) return refuse(cmd.eventId(), epochProblem);

        FailoverAction<?> action = actions.forCommand(cmd.getClass());
        if (action == null) return refuse(cmd.eventId(), "no-action");

        String roleProblem = gateRole(action);
        if (roleProblem != null) return refuse(cmd.eventId(), roleProblem);

        CommandResultEvent result = executeRemembered(action, cmd);
        return wrap(result);
    }

    // ── the gates ──

    /** Gate 1 — identity: plugin id + version must match a locally loaded plugin. */
    private PluginDescriptor gateIdentity(JsonNode node) {
        String pluginId = node.path("pluginId").asText(null);
        String pluginVersion = node.path("pluginVersion").asText(null);
        if (pluginId == null || pluginVersion == null) return null;
        PluginDescriptor d = plugins.byId(pluginId);
        if (d == null || !d.pluginVersion().equals(pluginVersion)) {
            LOG.warnf("command refused: version-mismatch pluginId=%s version=%s", pluginId, pluginVersion);
            return null;
        }
        return d;
    }

    /** Typed parse — only after the class name passes the descriptor whitelist. */
    private CommandEvent parseWhitelisted(PluginDescriptor plugin, CommandEnvelope envelope) {
        Optional<Class<? extends CommandEvent>> cls = plugins.commandClass(plugin, envelope.commandClass());
        if (cls.isEmpty()) {
            LOG.warnf("command refused: class %s not declared by plugin %s",
                    envelope.commandClass(), plugin.pluginId());
            return null;
        }
        try {
            return json.treeToValue(envelope.command(), cls.get());
        } catch (Exception e) {
            LOG.warnf("command refused: typed parse failed for %s: %s", envelope.commandClass(), e.getMessage());
            return null;
        }
    }

    /** Gate 2 — address: a misrouted order dies loudly, never executes quietly. */
    private String gateAddress(CommandEvent cmd) {
        return cfg.nodeName().equals(cmd.targetNode()) ? null : "wrong-node";
    }

    /** Gate 3 — epoch: a command from an older failover is a ghost. */
    private String gateEpoch(CommandEvent cmd) {
        try {
            long current = gate.epoch();
            return cmd.failoverEpoch() >= current ? null : "epoch-stale";
        } catch (Exception e) {
            LOG.warnf("epoch unverifiable: %s", e.getMessage());
            return "epoch-unverifiable";
        }
    }

    /** Role gate — only when the action declares roles; unlisted node ⇒ refuse. */
    private String gateRole(FailoverAction<?> action) {
        Set<String> allowed = action.allowedRoles();
        if (allowed.isEmpty()) return null;
        Set<String> mine = roles.rolesOf(cfg.nodeName());
        return mine.stream().anyMatch(allowed::contains) ? null : "role-not-permitted";
    }

    // ── gate 4 (replay) + the work ──

    private CommandResultEvent executeRemembered(FailoverAction<?> action, CommandEvent cmd) {
        try {
            return replays.executeOnce(cmd.eventId(), () -> invoke(action, cmd));
        } catch (RuntimeException e) {
            LOG.warnf(e, "action %s threw for command %s", action.id(), cmd.eventId());
            return CommandRefused.of(CorePluginDescriptor.PLUGIN_ID, CorePluginDescriptor.PLUGIN_VERSION,
                    cmd.eventId(), "action-threw: " + e.getMessage());
        }
    }

    /**
     * Erased bridge — the action's command type and the parsed command are
     * the same class by the whitelist + registry construction above.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private CommandResultEvent invoke(FailoverAction action, CommandEvent cmd) {
        ActionContext ctx = new ActionContext<>(
                cfg.clusterName(),
                cfg.nodeName(),
                Duration.ofSeconds(failoverCfg.actionDeadlineSec()),
                cmd,
                fabric);
        Optional<CommandResultEvent> done = action.checkIdempotent(ctx);
        if (done.isPresent()) return done.get();
        return action.execute(ctx);
    }

    // ── replies ──

    private ResultEnvelope refuse(String inReplyTo, String reason) {
        return wrap(CommandRefused.of(CorePluginDescriptor.PLUGIN_ID, CorePluginDescriptor.PLUGIN_VERSION,
                inReplyTo, reason));
    }

    private ResultEnvelope wrap(CommandResultEvent result) {
        return new ResultEnvelope(result.getClass().getName(), json.valueToTree(result));
    }
}
