package com.tb.nw.core.domain.api;

import java.util.List;

/**
 * One service inside a failover group: what it is, which plugin drives it, its
 * role, and where it sits in the promote order. A member never fails over by
 * itself — the group does, walking its members in order.
 */
public record Member(
        String type,            // "mysql", "sigtran", "redis", "freeswitch", ...
        String pluginId,        // the plugin that supplies this member's probes + actions
        String role,            // "master-slave" | "active-standby"
        int order,              // promote order within the group (ascending)
        List<String> dependsOn  // member types that must promote before this one
) {
    public Member {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
