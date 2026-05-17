package com.tb.nw.spi;

import java.util.Set;

/**
 * Single probe unit. Implementations come from built-in plugins (compile-time)
 * or script plugins (runtime descriptors invoking executables).
 *
 * Implementations MUST be stateless across probe() calls — the agent may
 * instantiate once and invoke repeatedly from a scheduler thread.
 */
public interface HealthCheck {

    /** Globally unique identifier, e.g. "mysql.local.master". */
    String id();

    /** Where the observation is made from. */
    Vantage vantage();

    /**
     * Facets this probe requires to be applicable on the host. If any are
     * absent, the agent's runner skips the tick — no observation is published.
     * Default: empty, meaning "run anywhere."
     */
    default Set<String> requiredFacets() {
        return Set.of();
    }

    /** Do the work. Must respect ctx.deadline(); should not throw — return UNKNOWN on internal errors. */
    HealthReport probe(ProbeContext ctx);
}
