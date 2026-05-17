package com.tb.nw.spi;

import java.util.Optional;
import java.util.Set;

/**
 * A side-effecting operation invoked by the Dispatcher on a target node.
 * Authorization is enforced separately at the ActionEndpoint — implementations
 * focus on the operation itself.
 *
 * For multi-step actions (Promote, Fence, RaiseVip), implementations are
 * expected to use the routesphere state machine DSL internally; this interface
 * stays single-method so the dispatch protocol remains uniform.
 */
public interface FailoverAction {

    /** Globally unique identifier, e.g. "mysql.promote-self". */
    String id();

    /**
     * Roles permitted to invoke this action. Informational at the SPI level —
     * the authoritative check happens at the ActionEndpoint against the
     * cluster's permission matrix.
     */
    Set<String> allowedRoles();

    /**
     * If this idempotency token has already been processed, return the cached
     * result. Otherwise return empty and execute() will be called.
     */
    Optional<ActionResult> checkIdempotent(ActionContext ctx);

    /** Do the work. */
    ActionResult execute(ActionContext ctx);
}
