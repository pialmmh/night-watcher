package com.tb.nw.plugins.mock;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Knobs for the mock plugin. The mock "service" is a pair of files per node
 * under {@link #stateDir()}:
 *
 * <pre>
 *   {node}.state   fast | degraded | dead | fenced     (missing = dead)
 *   {node}.role    master | slave                      (missing = no service here)
 * </pre>
 *
 * Per-seat overrides let a single box simulate network partitions:
 * {@code {node}.state@client} / {@code {node}.state@peer} override what the
 * CLIENT / REMOTE_PEER probes see, leaving the local truth untouched —
 * that is how a minority seat is made to "lie" when demoing the vote.
 */
@ConfigMapping(prefix = "nw.mock")
public interface MockConfig {

    /** Directory holding the mock service state files. */
    @WithDefault("/tmp/nw-mock")
    String stateDir();

    /**
     * Node the CLIENT / REMOTE_PEER probes watch (the mock cluster's master).
     * Unset = those probes stay dormant on this host.
     */
    Optional<String> watchTarget();
}
