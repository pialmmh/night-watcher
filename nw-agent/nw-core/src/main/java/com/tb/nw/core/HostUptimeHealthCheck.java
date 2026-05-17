package com.tb.nw.core;

import com.tb.nw.spi.HealthCheck;
import com.tb.nw.spi.HealthReport;
import com.tb.nw.spi.ProbeContext;
import com.tb.nw.spi.Vantage;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Trivial built-in probe that reads /proc/uptime. Exists to prove the SPI
 * end-to-end before service-specific plugins land. Reports DEGRADED if the
 * host has been up &lt; 60 s (possibly mid-boot), FAST otherwise. UNKNOWN if
 * the file isn't readable (e.g. running on Windows under tests).
 */
@ApplicationScoped
public class HostUptimeHealthCheck implements HealthCheck {

    private static final Path UPTIME = Path.of("/proc/uptime");

    @Override public String id() { return "host.uptime"; }
    @Override public Vantage vantage() { return Vantage.LOCAL_SELF; }

    @Override public HealthReport probe(ProbeContext ctx) {
        Instant start = Instant.now();
        long uptimeSec = readUptimeSeconds();
        if (uptimeSec < 0) return HealthReport.unknown("/proc/uptime unreadable");
        Duration latency = Duration.between(start, Instant.now());
        Map<String, Object> detail = Map.of(
                "uptime_seconds", uptimeSec,
                "uptime_human", humanize(uptimeSec));
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
