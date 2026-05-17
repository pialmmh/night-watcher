package com.tb.nw.spi;

import java.time.Duration;
import java.util.Map;

/**
 * Handed to every HealthCheck.probe() invocation. The agent constructs the
 * record at tick time and passes it to the probe.
 */
public record ProbeContext(
        String clusterName,
        String localNode,
        Duration deadline,
        Map<String, Object> probeConfig
) {}
