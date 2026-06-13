package com.tb.nw.core.domain.dependencies;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * The uniform failover-domain config — the same schema for one service or five.
 * Maps {@code nw.domains[*]} from the tenant profile YAML. A single-service
 * domain is just a member list of length one; there is no separate shape.
 *
 * <p>SmallRye maps method names to kebab keys: {@code pluginId()} → {@code
 * plugin-id}, {@code activeNode()} → {@code active-node}, etc.</p>
 */
@ConfigMapping(prefix = "nw")
public interface FailoverDomainConfig {

    Optional<List<DomainDef>> domains();

    interface DomainDef {
        String id();
        List<MemberDef> members();
        Optional<VipDef> vip();
        @WithDefault("SELF") String fence();
        String trigger();          // e.g. "member-odown:mysql"
        String activeNode();
        String standbyNode();
    }

    interface MemberDef {
        String type();             // "mysql", "sigtran", "redis", ...
        String pluginId();
        @WithDefault("active-standby") String role();
        int order();
        Optional<List<String>> dependsOn();
    }

    interface VipDef {
        String mode();             // "bgp"
        String prefix();           // "10.x.y.Z/32"
    }
}
