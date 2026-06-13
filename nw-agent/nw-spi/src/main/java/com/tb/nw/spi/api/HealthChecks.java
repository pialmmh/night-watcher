package com.tb.nw.spi.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Composes several small named checks into one health verdict — <b>worst
 * wins</b>. This is how every role/plugin builds a probe from one method per
 * check, instead of a single tangled grading function:
 *
 * <pre>
 *   HealthChecks.builder()
 *       .check("systemctl", () -> active   ? CheckResult.up() : CheckResult.down("inactive"))
 *       .check("disk",      () -> freeBytes ? CheckResult.up() : CheckResult.down("disk full"))
 *       .check("read-only", () -> !readOnly ? CheckResult.up() : CheckResult.degraded("read-only"))
 *       .run();
 * </pre>
 *
 * <p>Rules: <b>UP</b> = every check passes · <b>DEGRADED</b> = at least one
 * degrading check fails (and none down) · <b>DOWN</b> = a critical check fails.
 * A check that throws counts as down. No checks at all ⇒ UNKNOWN.</p>
 */
public final class HealthChecks {

    private final List<Named> checks;

    private HealthChecks(List<Named> checks) { this.checks = checks; }

    public static Builder builder() { return new Builder(); }

    public Verdict run() {
        if (checks.isEmpty()) return new Verdict(HealthState.UNKNOWN, List.of());
        HealthState worst = HealthState.FAST;
        List<String> failures = new ArrayList<>();
        for (Named n : checks) {
            CheckResult r;
            try {
                r = n.check().get();
            } catch (Exception e) {
                r = CheckResult.down(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            if (!r.passed()) failures.add(n.name() + ": " + r.detail());
            if (severity(r.state()) > severity(worst)) worst = r.state();
        }
        return new Verdict(worst, List.copyOf(failures));
    }

    private static int severity(HealthState s) {
        return switch (s) {
            case FAST -> 0;
            case DEGRADED, UNKNOWN -> 1;
            case DEAD -> 2;
        };
    }

    /** The folded outcome of all checks: the worst state, and which checks failed and why. */
    public record Verdict(HealthState state, List<String> failures) {
        public boolean up() { return state == HealthState.FAST; }
        public String summary() {
            return failures.isEmpty() ? "all checks passed" : String.join("; ", failures);
        }
    }

    private record Named(String name, Supplier<CheckResult> check) {}

    public static final class Builder {
        private final List<Named> checks = new ArrayList<>();

        public Builder check(String name, Supplier<CheckResult> check) {
            checks.add(new Named(name, check));
            return this;
        }

        public HealthChecks build() { return new HealthChecks(List.copyOf(checks)); }

        public Verdict run() { return build().run(); }
    }
}
