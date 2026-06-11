package com.tb.nw.spi;

import com.tb.nw.fabric.api.Fabric;

import java.time.Duration;

/**
 * Handed to every {@link FailoverAction#execute} invocation. Carries
 * identity, the typed command event the Dispatcher sent, and per-call
 * scaffolding.
 *
 * <p>The framework validates the command's {@link CommandEvent#pluginVersion}
 * against the locally loaded plugin's {@link PluginDescriptor} before
 * dispatching, so an action implementation never sees a version-mismatched
 * command.</p>
 *
 * @param <P> plugin-specific {@link CommandEvent} subtype
 */
public record ActionContext<P extends CommandEvent>(
        String clusterName,
        String localNode,
        Duration deadline,
        P command,
        Fabric fabric
) {
    /** Convenience — the failover epoch the command was minted in. */
    public long failoverEpoch() { return command.failoverEpoch(); }

    /** Convenience — the command's correlation id (also its idempotency key). */
    public String commandEventId() { return command.eventId(); }
}
