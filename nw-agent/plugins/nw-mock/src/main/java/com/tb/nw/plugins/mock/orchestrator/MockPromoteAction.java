package com.tb.nw.plugins.mock.orchestrator;

import com.tb.nw.plugins.mock.MockPluginDescriptor;
import com.tb.nw.plugins.mock.events.MockCommandResult;
import com.tb.nw.plugins.mock.events.MockPromoteCommand;
import com.tb.nw.plugins.mock.probe.MockStateStore;
import com.tb.nw.spi.ActionContext;
import com.tb.nw.spi.CommandResultEvent;
import com.tb.nw.spi.FailoverAction;
import com.tb.nw.spi.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mock promotion surgery, executed locally by the chosen standby's own
 * agent: role file → {@code master}, state file → {@code fast}. Idempotent:
 * already master → ok without work.
 */
@ApplicationScoped
public class MockPromoteAction implements FailoverAction<MockPromoteCommand> {

    private static final Logger LOG = Logger.getLogger(MockPromoteAction.class);

    @Inject MockPluginDescriptor descriptor;
    @Inject MockStateStore store;

    @Override public String id() { return "mock.promote-self"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MockPromoteCommand> commandType() { return MockPromoteCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }

    @Override
    public Optional<CommandResultEvent> checkIdempotent(ActionContext<MockPromoteCommand> ctx) {
        if ("master".equals(store.roleOf(ctx.command().targetNode()).orElse(null))) {
            return Optional.of(MockCommandResult.ok(ctx.commandEventId(), "mock.promote",
                    Map.of("role", "master", "note", "already master")));
        }
        return Optional.empty();
    }

    @Override
    public CommandResultEvent execute(ActionContext<MockPromoteCommand> ctx) {
        String node = ctx.command().targetNode();
        try {
            store.writeRole(node, "master");
            store.writeState(node, "fast");
            LOG.infof("mock promotion executed: %s.role → master (epoch %d)", node, ctx.failoverEpoch());
            return MockCommandResult.ok(ctx.commandEventId(), "mock.promote",
                    Map.of("role", "master", "state", "fast"));
        } catch (Exception e) {
            return MockCommandResult.failure(ctx.commandEventId(), "mock.promote",
                    "write-failed: " + e.getMessage());
        }
    }
}
