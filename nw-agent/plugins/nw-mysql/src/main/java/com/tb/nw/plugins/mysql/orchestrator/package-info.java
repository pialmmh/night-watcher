/**
 * MySQL-specific orchestrator surface — the act side of the plugin.
 *
 * <p>Reserved for implementations of the night-watcher orchestration SPIs:</p>
 *
 * <ul>
 *   <li>{@link com.tb.nw.spi.api.FailoverAction} —
 *       <em>MysqlFenceSelf</em>, <em>MysqlPromoteSelf</em>, <em>MysqlSetReadOnly</em>,
 *       <em>MysqlStartReplica</em>, <em>MysqlStopReplica</em>, …</li>
 *   <li>{@link com.tb.nw.spi.api.HealthAggregator} — <em>MysqlHealthAggregator</em>
 *       (reduces a vantage bucket of observations to a 0.0–1.0 score)</li>
 *   <li>{@link com.tb.nw.spi.api.CandidateSelector} — <em>MysqlCandidateSelector</em>
 *       (picks promotion target by LSN / uptime / hostname tiebreakers)</li>
 *   <li>{@link com.tb.nw.spi.api.FailoverPlanGenerator} — <em>MysqlPlanGenerator</em>
 *       (builds the ordered Plan the Dispatcher walks)</li>
 *   <li>{@link com.tb.nw.spi.api.ClusterCondition} — predicates the Activation
 *       engine evaluates against the cluster topology</li>
 * </ul>
 *
 * <p>Currently empty — the generic {@code FailoverCoordinator} in
 * {@code nw-core/coord} drives the demo by printing console lines. When the
 * real Dispatcher + ActionEndpoint land, the MySQL impls land here.</p>
 */
package com.tb.nw.plugins.mysql.orchestrator;
