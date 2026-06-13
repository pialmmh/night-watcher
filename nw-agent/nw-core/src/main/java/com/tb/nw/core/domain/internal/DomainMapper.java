package com.tb.nw.core.domain.internal;

import com.tb.nw.core.domain.api.FailoverGroup;
import com.tb.nw.core.domain.api.FencePolicy;
import com.tb.nw.core.domain.api.Member;
import com.tb.nw.core.domain.api.Vip;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.DomainDef;

import java.util.List;
import java.util.Optional;

/** Turns one declared domain (config) into a validated {@link FailoverGroup}. Pure. */
public final class DomainMapper {

    private DomainMapper() {}

    public static FailoverGroup toGroup(DomainDef d) {
        List<Member> members = d.members().stream()
                .map(m -> new Member(m.type(), m.pluginId(), m.role(), m.order(),
                        m.dependsOn().orElse(List.of())))
                .toList();
        Optional<Vip> vip = d.vip().map(v -> new Vip(v.mode(), v.prefix()));
        FencePolicy fence = FencePolicy.valueOf(d.fence().toUpperCase());
        FailoverGroup group = new FailoverGroup(d.id(), members, vip, fence,
                d.trigger(), d.activeNode(), d.standbyNode());
        group.validateOrdering();
        return group;
    }
}
