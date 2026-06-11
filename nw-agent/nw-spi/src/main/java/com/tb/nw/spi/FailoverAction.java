package com.tb.nw.spi;

import java.util.Optional;
import java.util.Set;

/**
 * A side-effecting operation invoked by the Dispatcher on a target node.
 * Authorization is enforced separately at the ActionEndpoint — implementations
 * focus on the operation itself.
 *
 * <p>For multi-step actions (Promote, Fence, RaiseVip), implementations are
 * expected to use the routesphere state machine DSL internally; this interface
 * stays single-method so the dispatch protocol remains uniform.</p>
 *
 * <p>The {@code P} type parameter is the plugin's typed {@link CommandEvent} —
 * what the Dispatcher includes in the request, what {@code execute()} reads.
 * The framework refuses to invoke an action whose command's
 * {@code pluginVersion} doesn't match the locally loaded plugin.</p>
 *
 * @param <P> plugin-specific command event
 */
public interface FailoverAction<P extends CommandEvent> {

    /** Globally unique identifier, e.g. {@code "mysql.promote-self"}. */
    String id();

    /** Owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /** The concrete {@link CommandEvent} subtype this action consumes. */
    Class<P> commandType();

    /**
     * Roles permitted to invoke this action. Informational at the SPI level —
     * the authoritative check happens at the ActionEndpoint against the
     * cluster's permission matrix.
     */
    Set<String> allowedRoles();

    /**
     * If this command's {@link CommandEvent#eventId} has already been
     * processed, return the cached result. Otherwise return empty and
     * {@code execute()} will be called.
     */
    Optional<CommandResultEvent> checkIdempotent(ActionContext<P> ctx);

    /** Do the work. */
    CommandResultEvent execute(ActionContext<P> ctx);
}
