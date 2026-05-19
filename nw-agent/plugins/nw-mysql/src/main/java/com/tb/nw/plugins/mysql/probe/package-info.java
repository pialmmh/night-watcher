/**
 * MySQL-specific probe surface — the observe side of the plugin.
 *
 * <p>Holds every class that <em>reads</em> cluster state without changing it:</p>
 *
 * <ul>
 *   <li>{@link com.tb.nw.spi.FacetDetector} — {@code MysqlFacetDetector}
 *       publishes the local mysql-*-here facet set.</li>
 *   <li>{@link com.tb.nw.spi.HealthCheck} implementations — currently only
 *       {@code MySqlRemoteClient} is CDI-active. The four local / remote
 *       master+slave variants are preserved as dormant source (the
 *       {@code @ApplicationScoped} annotation is intentionally absent so
 *       Quarkus does not register them as beans).</li>
 *   <li>{@code MysqlClassifier} — package-private helper that maps a probe's
 *       latency to {@code FAST} / {@code DEGRADED} / {@code DEAD}.</li>
 * </ul>
 *
 * <p>Shared dependencies ({@link com.tb.nw.plugins.mysql.MysqlConfig},
 * {@link com.tb.nw.plugins.mysql.MysqlConnections}) live in the parent
 * package so both this package and
 * {@link com.tb.nw.plugins.mysql.orchestrator} can use them without a
 * cyclic dependency.</p>
 */
package com.tb.nw.plugins.mysql.probe;
