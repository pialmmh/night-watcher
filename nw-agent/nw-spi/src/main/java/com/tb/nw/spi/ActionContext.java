package com.tb.nw.spi;

import com.tb.nw.fabric.api.Fabric;

import java.time.Duration;

/**
 * Handed to every {@link FailoverAction#execute} invocation. Carries
 * identity, epoch, idempotency token, and the typed payload supplied by the
 * Dispatcher.
 *
 * <p>The payload is plugin-typed: the framework validates its
 * {@code pluginVersion} against the locally loaded plugin's
 * {@link PluginDescriptor} before dispatching, so an action implementation
 * never sees a version-mismatched payload.</p>
 *
 * <p>SshExecutor / JdbcSource / Logger / Tracer are deferred until those
 * layers land; plugins open their own JDBC connections from service-specific
 * helpers in the meantime.</p>
 *
 * @param <P> plugin-specific action payload
 */
public record ActionContext<P extends PluginEntity>(
        String clusterName,
        String localNode,
        long failoverEpoch,
        String idempotencyToken,
        Duration deadline,
        P payload,
        Fabric fabric
) {}
