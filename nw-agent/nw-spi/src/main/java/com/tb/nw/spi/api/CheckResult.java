package com.tb.nw.spi.api;

/**
 * One check's outcome inside a composite health probe: a level plus a short
 * human reason. A probe is built from many small checks (one method each);
 * {@link HealthChecks} folds their outcomes into a single verdict.
 *
 * <ul>
 *   <li>{@link #up()} — this check passed.</li>
 *   <li>{@link #degraded(String)} — this check failed but the service still
 *       serves (e.g. a master gone read-only, a replica lagging).</li>
 *   <li>{@link #down(String)} — a critical check failed; the service is down
 *       even if other parts answer (e.g. systemctl inactive, disk full).</li>
 * </ul>
 */
public record CheckResult(HealthState state, String detail) {

    public static CheckResult up() { return new CheckResult(HealthState.FAST, "ok"); }

    public static CheckResult up(String detail) { return new CheckResult(HealthState.FAST, detail); }

    public static CheckResult degraded(String why) { return new CheckResult(HealthState.DEGRADED, why); }

    public static CheckResult down(String why) { return new CheckResult(HealthState.DEAD, why); }

    public boolean passed() { return state == HealthState.FAST; }
}
