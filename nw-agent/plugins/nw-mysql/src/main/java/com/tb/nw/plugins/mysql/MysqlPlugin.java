package com.tb.nw.plugins.mysql;

/**
 * Marker class for the MySQL plugin module. Real components (FacetDetector,
 * HealthChecks, FailoverActions, HealthAggregator, CandidateSelector,
 * FailoverPlanGenerator) land in this package as they're implemented.
 */
public final class MysqlPlugin {
    private MysqlPlugin() {}

    public static final String SERVICE_TYPE = "mysql";
}
