package com.tb.nw.plugins.mysql.entities;

import com.tb.nw.plugins.mysql.MysqlPluginDescriptor;
import com.tb.nw.spi.PluginEntity;

/**
 * Typed body of every observation produced by an nw-mysql probe.
 *
 * <p>Replaces the legacy {@code Map<String, Object> detail} with strict
 * fields. Future probes (master / slave / remote variants) reuse this same
 * record; vantage-specific fields stay nullable / zero when not applicable
 * so the record's shape is stable across probe kinds.</p>
 *
 * <p>{@link #pluginId} and {@link #pluginVersion} are baked into every
 * instance via the static factories — instances constructed with the
 * canonical constructor must pass the matching descriptor constants.</p>
 */
public record MysqlObservationDetail(
        String pluginId,
        String pluginVersion,

        /* target / connection metadata */
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
) implements PluginEntity {

    /* ─── factories — every constructor stamps the current plugin version ─── */

    public static MysqlObservationDetail ofClientProbe(
            String targetHost, String query,
            boolean canaryOk, long queryLatencyNanos, boolean rowsReturned,
            String errorMessage) {
        return new MysqlObservationDetail(
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                targetHost, query,
                canaryOk, queryLatencyNanos, rowsReturned, errorMessage,
                0L, 0L, null,
                null, null, null, null, null);
    }

    public static MysqlObservationDetail noTargetConfigured(String reason) {
        return new MysqlObservationDetail(
                MysqlPluginDescriptor.PLUGIN_ID,
                MysqlPluginDescriptor.PLUGIN_VERSION,
                null, null,
                false, 0L, false, reason,
                0L, 0L, null,
                null, null, null, null, null);
    }
}
