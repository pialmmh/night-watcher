package com.tb.nw.plugins.mock.internal;

import com.tb.nw.plugins.mock.dependencies.MockConfig;
import com.tb.nw.spi.api.HealthState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The mock service's "wire protocol": files under the state dir.
 *
 * <pre>
 *   {node}.state          fast | degraded | dead | fenced   (missing = absent → DEAD)
 *   {node}.state@client   per-seat override — what the CLIENT probe sees
 *   {node}.state@peer     per-seat override — what the REMOTE_PEER probe sees
 *   {node}.role           master | slave                    (missing = no service here)
 * </pre>
 *
 * Reads are the probes' "connections"; writes are the actions' "surgery".
 */
@ApplicationScoped
public class MockStateStore {

    public static final String ABSENT = "absent";
    public static final String FENCED = "fenced";

    @Inject MockConfig cfg;

    /** Injected, not located — the plugin can't depend on core's AgentConfig type. */
    @Inject @ConfigProperty(name = "nw.agent.node-name", defaultValue = "unknown")
    String nodeName;

    public String localNode() {
        return nodeName;
    }

    public boolean stateDirExists() {
        return Files.isDirectory(Path.of(cfg.stateDir()));
    }

    /** Plain truth — the node's own state file. */
    public String stateOf(String node) {
        return read(node + ".state").orElse(ABSENT);
    }

    /** Seat view — the per-vantage override file wins when present. */
    public String stateSeenBy(String node, String seatSuffix) {
        return read(node + ".state@" + seatSuffix)
                .or(() -> read(node + ".state"))
                .orElse(ABSENT);
    }

    public Optional<String> roleOf(String node) {
        return read(node + ".role");
    }

    public static HealthState grade(String rawState) {
        return switch (rawState) {
            case "fast" -> HealthState.FAST;
            case "degraded" -> HealthState.DEGRADED;
            case "dead", FENCED, ABSENT -> HealthState.DEAD;
            default -> HealthState.UNKNOWN;
        };
    }

    /* ── surgery (actions write through here) ── */

    public void writeState(String node, String state) throws IOException {
        write(node + ".state", state);
    }

    public void writeRole(String node, String role) throws IOException {
        write(node + ".role", role);
    }

    /* ── file plumbing ── */

    private Optional<String> read(String fileName) {
        Path p = Path.of(cfg.stateDir(), fileName);
        try {
            if (!Files.isRegularFile(p)) return Optional.empty();
            String s = Files.readString(p).trim().toLowerCase(Locale.ROOT);
            return s.isEmpty() ? Optional.empty() : Optional.of(s);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void write(String fileName, String content) throws IOException {
        Path dir = Path.of(cfg.stateDir());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), content + "\n");
    }
}
