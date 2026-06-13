package com.tb.nw.plugins.mysql.dependencies;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Host-level checks for the local MySQL the SQL probe cannot see by itself: is
 * the service unit active, does the data disk still have room. Best-effort — if
 * the tool is missing or times out we trust the SQL path and report healthy,
 * never a false DOWN. Only a definite "inactive/failed" or a full disk fails.
 */
@ApplicationScoped
public class MysqlOsProbe {

    @Inject MysqlConfig cfg;

    /** {@code systemctl is-active <service>} — false only on a definite inactive/failed. */
    public boolean serviceActive() {
        String out = run("systemctl", "is-active", cfg.serviceName());
        if (out == null) return true;                      // no systemd / failure → trust SQL
        String s = out.strip();
        return !(s.equals("inactive") || s.equals("failed") || s.equals("deactivating"));
    }

    /** Available KB on the data directory's filesystem — false only when it hits zero. */
    public boolean dataDiskHasSpace() {
        String out = run("df", "-Pk", cfg.dataDir());
        if (out == null) return true;
        try {
            String[] lines = out.strip().split("\\R");
            String[] cols = lines[lines.length - 1].trim().split("\\s+");
            long availKb = Long.parseLong(cols[3]);        // Filesystem 1024-blocks Used Available ...
            return availKb > 0;
        } catch (Exception e) {
            return true;
        }
    }

    private String run(String... cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                out = r.lines().collect(Collectors.joining("\n"));
            }
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return out;
        } catch (Exception e) {
            if (p != null) p.destroyForcibly();
            return null;
        }
    }
}
