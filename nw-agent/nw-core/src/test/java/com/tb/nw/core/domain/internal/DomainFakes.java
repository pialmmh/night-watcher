package com.tb.nw.core.domain.internal;

import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.DomainDef;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.MemberDef;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.VipDef;

import java.util.List;
import java.util.Optional;

/** Plain records standing in for the config-mapping interfaces in unit tests. */
public final class DomainFakes {
    private DomainFakes() {}

    public record FakeMember(String type, String pluginId, String role, int order,
                             Optional<List<String>> dependsOn) implements MemberDef {}

    public record FakeVip(String mode, String prefix) implements VipDef {}

    public record FakeDomain(String id, List<MemberDef> members, Optional<VipDef> vip,
                             String fence, String trigger, String activeNode,
                             String standbyNode) implements DomainDef {}

    public static MemberDef member(String type, int order, String... deps) {
        return new FakeMember(type, "nw-" + type, "active-standby", order,
                deps.length == 0 ? Optional.empty() : Optional.of(List.of(deps)));
    }

    public static DomainDef domain(String id, String active, String standby, List<MemberDef> members) {
        return new FakeDomain(id, members, Optional.empty(), "SELF",
                "member-odown:mysql", active, standby);
    }
}
