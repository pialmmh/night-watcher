package com.tb.nw.spi;

import java.util.Set;

/**
 * Single probe unit. Implementations come from built-in plugins (compile-time)
 * or script plugins (runtime descriptors invoking executables).
 *
 * <p>Implementations MUST be stateless across {@code probe()} calls — the
 * agent may instantiate once and invoke repeatedly from a scheduler thread.</p>
 *
 * <p>Strict typing: a probe declares the {@link PluginEntity} type of the
 * detail it produces. The framework uses this type when serializing
 * observations to etcd and (with the matching {@link PluginDescriptor}) when
 * deserializing them back at consumption sites.</p>
 *
 * @param <T> plugin-specific observation detail
 */
public interface HealthCheck<T extends PluginEntity> {

    /** Globally unique probe identifier, e.g. {@code "mysql.remote.client"}. */
    String id();

    /** Where the observation is made from. */
    Vantage vantage();

    /** The owning plugin's descriptor. */
    PluginDescriptor descriptor();

    /**
     * The {@link PluginEntity} subclass this probe returns as observation
     * detail. Used by the framework for Jackson deserialization of inbound
     * observations from etcd.
     */
    Class<T> detailType();

    /**
     * Facets this probe requires to be applicable on the host. If any are
     * absent, the agent's runner skips the tick — no observation is published.
     * Default: empty, meaning "run anywhere."
     */
    default Set<String> requiredFacets() {
        return Set.of();
    }

    /**
     * The node id being probed. Default is the local node — correct for
     * {@code LOCAL_SELF} vantage. Remote / client probes override to return
     * the remote target's id so the published Observation's {@code target}
     * field correctly identifies the subject.
     *
     * <p>Returning {@code null} short-circuits the tick (no observation
     * published) — useful when the probe is technically applicable but the
     * remote target is not yet configured.</p>
     */
    default String target(ProbeContext ctx) {
        return ctx.localNode();
    }

    /** Do the work. Must respect {@code ctx.deadline()}; should not throw — return UNKNOWN with a populated detail on internal errors. */
    HealthReport<T> probe(ProbeContext ctx);
}
