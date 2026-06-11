package com.tb.nw.core.observe;

import com.tb.nw.core.CorePluginDescriptor;

import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.HealthState;
import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.ProbeContext;
import com.tb.nw.spi.Vantage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Trivial built-in probe that reads {@code /proc/uptime}. Exists to prove
 * the SPI end-to-end before service-specific plugins land.
 *
 * <p>Reports {@code DEGRADED} if the host has been up &lt; 60 s (possibly
 * mid-boot), {@code FAST} otherwise. {@code UNKNOWN} if the file isn't
 * readable (e.g. running on Windows under tests).</p>
 */
@ApplicationScoped
public class HostUptimeHealthCheck implements HealthCheck<HostUptimeEvent> {

    private static final Path UPTIME = Path.of("/proc/uptime");

    @Inject CorePluginDescriptor descriptor;

    @Override public String id() { return "host.uptime"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<HostUptimeEvent> eventType() { return HostUptimeEvent.class; }

    @Override public HealthReport<HostUptimeEvent> probe(ProbeContext ctx) {
        Instant start = Instant.now();
        long uptimeSec = readUptimeSeconds();
        String target = ctx.localNode();

        if (uptimeSec < 0) {
            return HealthReport.of(
                    HostUptimeEvent.unreadable(target, "/proc/uptime unreadable"),
                    Duration.ZERO);
        }

        Duration latency = Duration.between(start, Instant.now());
        HealthState state = uptimeSec < 60 ? HealthState.DEGRADED : HealthState.FAST;
        HostUptimeEvent event = HostUptimeEvent.ok(target, state, uptimeSec, humanize(uptimeSec));
        return HealthReport.of(event, latency);
    }

    private static long readUptimeSeconds() {
        try {
            String first = Files.readString(UPTIME).trim().split("\\s+")[0];
            return (long) Double.parseDouble(first);
        } catch (IOException | NumberFormatException e) {
            return -1L;
        }
    }

    private static String humanize(long s) {
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + (s % 60) + "s";
    }
}
