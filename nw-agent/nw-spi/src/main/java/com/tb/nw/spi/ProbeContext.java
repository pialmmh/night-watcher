package com.tb.nw.spi;

import com.tb.nw.fabric.api.Fabric;

import java.time.Duration;

/**
 * Handed to every {@link HealthCheck#probe} invocation. The agent constructs
 * the record at tick time and passes it to the probe.
 *
 * <p>Per-investigator config (the old {@code Map<String,Object> probeConfig})
 * has been removed: plugins read their own typed {@code @ConfigMapping} bean
 * directly via CDI injection. When the activation engine lands and per-entry
 * overrides become useful, a typed {@code C extends PluginEntity} parameter
 * will be added here.</p>
 *
 * <p>Logger / Tracer / Meter are deferred until the observability layer lands;
 * plugins log via their own slf4j / jboss-logging in the meantime.</p>
 */
public record ProbeContext(
        String clusterName,
        String localNode,
        Duration deadline,
        Fabric fabric
) {}
