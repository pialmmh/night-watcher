package com.tb.nw.core;

import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
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
 * the SPI end-to-end before service-specific plugins land. Reports
 * {@code DEGRADED} if the host has been up &lt; 60 s (possibly mid-boot),
 * {@code FAST} otherwise. {@code UNKNOWN} if the file isn't readable
 * (e.g. running on Windows under tests).
 */
@ApplicationScoped
public class HostUptimeHealthCheck implements HealthCheck<HostUptimeDetail> {

    private static final Path UPTIME = Path.of("/proc/uptime");

    @Inject CorePluginDescriptor descriptor;

    @Override public String id() { return "host.uptime"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }
    @Override public PluginDescriptor descriptor() { return descriptor; }
    @Override public Class<HostUptimeDetail> detailType() { return HostUptimeDetail.class; }

    @Override public HealthReport<HostUptimeDetail> probe(ProbeContext ctx) {
        Instant start = Instant.now();
        long uptimeSec = readUptimeSeconds();
        if (uptimeSec < 0) {
            return HealthReport.unknown(Duration.ZERO,
                    HostUptimeDetail.unreadable("/proc/uptime unreadable"));
        }
        Duration latency = Duration.between(start, Instant.now());
        HostUptimeDetail detail = HostUptimeDetail.ok(uptimeSec, humanize(uptimeSec));
        return uptimeSec < 60
                ? HealthReport.degraded(latency, detail)
                : HealthReport.fast(latency, detail);
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
