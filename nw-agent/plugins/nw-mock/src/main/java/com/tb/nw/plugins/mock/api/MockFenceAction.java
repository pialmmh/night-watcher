package com.tb.nw.plugins.mock.api;

import com.tb.nw.plugins.mock.api.MockPluginDescriptor;
import com.tb.nw.plugins.mock.publishes.MockCommandResult;
import com.tb.nw.plugins.mock.publishes.MockFenceCommand;
import com.tb.nw.plugins.mock.internal.MockStateStore;
import com.tb.nw.spi.api.ActionContext;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.FailoverAction;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mock fence surgery, executed locally by the dead master's own agent:
 * write {@code fenced} into this node's state file — from then on every seat
 * reads it as refusing connections. Idempotent: already fenced → ok.
 */
@ApplicationScoped
public class MockFenceAction implements FailoverAction<MockFenceCommand> {

    private static final Logger LOG = Logger.getLogger(MockFenceAction.class);

    @Inject MockPluginDescriptor descriptor;
    @Inject MockStateStore store;

    @Override public String id() { return "mock.fence-self"; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<MockFenceCommand> commandType() { return MockFenceCommand.class; }
    @Override public Set<String> allowedRoles() { return Set.of(); }

    @Override
    public Optional<CommandResultEvent> checkIdempotent(ActionContext<MockFenceCommand> ctx) {
        if (MockStateStore.FENCED.equals(store.stateOf(ctx.command().targetNode()))) {
            return Optional.of(MockCommandResult.ok(ctx.commandEventId(), "mock.fence",
                    Map.of("state", MockStateStore.FENCED, "note", "already fenced")));
        }
        return Optional.empty();
    }

    @Override
    public CommandResultEvent execute(ActionContext<MockFenceCommand> ctx) {
        String node = ctx.command().targetNode();
        try {
            store.writeState(node, MockStateStore.FENCED);
            LOG.infof("mock fence executed: %s.state → fenced (epoch %d)", node, ctx.failoverEpoch());
            return MockCommandResult.ok(ctx.commandEventId(), "mock.fence",
                    Map.of("state", MockStateStore.FENCED));
        } catch (Exception e) {
            return MockCommandResult.failure(ctx.commandEventId(), "mock.fence",
                    "write-failed: " + e.getMessage());
        }
    }
}
