package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.plugins.mysql.api.MysqlPluginDescriptor;
import com.tb.nw.spi.api.HealthState;
import com.tb.nw.spi.api.NwEvent;

import java.time.Instant;

/**
 * Health event emitted by the client-vantage MySQL probe. Replaces the
 * legacy untyped detail map with strict fields.
 *
 * <p>Vantage-specific fields (replica thread status, server uptime, etc.)
 * are nullable / zero when not applicable so the record's shape is stable
 * across the probe kinds that may emit it in future.</p>
 *
 * <p>Static factories stamp the current plugin id + version automatically;
 * authors using the canonical constructor must pass the descriptor constants.</p>
 */
public record MySqlRemoteHealth(
        String eventId,
        Instant timestamp,
        String pluginId,
        String pluginVersion,
        HealthState healthState,
        String target,

        /* connection / canary metadata */
        String targetHost,
        String query,

        /* canary outcome */
        boolean canaryOk,
        long queryLatencyNanos,
        boolean rowsReturned,
        String errorMessage,

        /* master-only fields (zero / null when not master) */
        long connectedReplicas,
        long serverUptimeSeconds,
        Boolean readOnly,

        /* slave-only fields (zero / null when not slave) */
        Boolean replicaIoRunning,
        Boolean replicaSqlRunning,
        Long secondsBehindMaster,
        String lastIoError,
        String lastSqlError
) implements MySqlHealthEvent {

    /* ─── factories ─────────────────────────────────────────────────────── */

    /**
     * CLIENT vantage — the app-impersonating canary. The master-side fields
     * ({@code readOnly} / {@code serverUptimeSeconds} / {@code connectedReplicas})
     * are best-effort enrichment gathered over the same client connection
     * after a successful canary — null / zero when the canary failed or the
     * enrichment queries were refused (e.g. a low-privilege canary user).
     */
    public static MySqlRemoteHealth ofClientProbe(
            HealthState state, String target,
            String targetHost, String query,
            boolean canaryOk, long queryLatencyNanos, boolean rowsReturned,
            String errorMessage,
            Boolean readOnly, long serverUptimeSeconds, long connectedReplicas) {
        return new MySqlRemoteHealth(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                state, target,
                targetHost, query,
                canaryOk, queryLatencyNanos, rowsReturned, errorMessage,
                connectedReplicas, serverUptimeSeconds, readOnly,
                null, null, null, null, null);
    }

    public static MySqlRemoteHealth noTargetConfigured(String target, String reason) {
        return new MySqlRemoteHealth(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                HealthState.UNKNOWN, target,
                null, null,
                false, 0L, false, reason,
                0L, 0L, null,
                null, null, null, null, null);
    }

    /**
     * LOCAL_SELF vantage — the local scout reporting what its own server
     * says: read-only flag, replica thread state, lag. This is the evidence
     * stream the CandidateSelector promotes from.
     */
    public static MySqlRemoteHealth ofLocalProbe(
            HealthState state, String target,
            long queryLatencyNanos, Boolean readOnly,
            Boolean replicaIoRunning, Boolean replicaSqlRunning, Long secondsBehindMaster,
            String lastIoError, String lastSqlError,
            long serverUptimeSeconds, long connectedReplicas,
            String errorMessage) {
        return new MySqlRemoteHealth(
                NwEvent.newEventId(),
                Instant.now(),
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                state, target,
                "127.0.0.1", "local-status",
                errorMessage == null, queryLatencyNanos, true, errorMessage,
                connectedReplicas, serverUptimeSeconds, readOnly,
                replicaIoRunning, replicaSqlRunning, secondsBehindMaster, lastIoError, lastSqlError);
    }
}
