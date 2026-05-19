package com.tb.nw.core.coord.events;

/**
 * Fired by the FailureTracker when N consecutive DEAD verdicts have landed
 * for the trigger investigator. Carries the target node id and the
 * investigator id so the FailoverCoordinator can include them in audit.
 */
public record MasterDeadDetected(String target, String investigator, int consecutiveFailures) {}
