package com.tb.nw.core.domain.api;

/**
 * How the dead node is kept from serving so the group can promote safely.
 * SELF = the dying node self-fences on quorum loss (the first cut); POWER and
 * NETWORK are stronger STONITH options for later.
 */
public enum FencePolicy { SELF, POWER, NETWORK }
