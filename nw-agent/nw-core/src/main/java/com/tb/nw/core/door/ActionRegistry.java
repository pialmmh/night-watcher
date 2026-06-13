package com.tb.nw.core.door;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.FailoverAction;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * CDI census of every {@link FailoverAction} on this agent, indexed by the
 * concrete command class it consumes. One action per command type — a
 * duplicate registration is a deployment error and fails fast.
 */
@ApplicationScoped
public class ActionRegistry {

    private static final Logger LOG = Logger.getLogger(ActionRegistry.class);

    @Inject Instance<FailoverAction<?>> actions;

    private final Map<Class<? extends CommandEvent>, FailoverAction<?>> byCommand = new HashMap<>();

    @PostConstruct
    void init() {
        for (FailoverAction<?> a : actions) {
            FailoverAction<?> prev = byCommand.put(a.commandType(), a);
            if (prev != null) {
                throw new IllegalStateException("two actions claim command type "
                        + a.commandType().getName() + ": " + prev.id() + " and " + a.id());
            }
        }
        LOG.infof("ActionRegistry: %d actions registered: %s",
                byCommand.size(), byCommand.values().stream().map(FailoverAction::id).toList());
    }

    /** The action consuming this command class, or null when none is deployed here. */
    public FailoverAction<?> forCommand(Class<? extends CommandEvent> commandClass) {
        return byCommand.get(commandClass);
    }
}
