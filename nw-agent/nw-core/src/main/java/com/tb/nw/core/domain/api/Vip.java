package com.tb.nw.core.domain.api;

/**
 * How a group's virtual IP follows the active node. Default mode {@code "bgp"}
 * announces the prefix as a /32 from the active node's FRR; the survivor keeps a
 * higher local-pref so a revived old node cannot win the route back.
 */
public record Vip(String mode, String prefix) {}
