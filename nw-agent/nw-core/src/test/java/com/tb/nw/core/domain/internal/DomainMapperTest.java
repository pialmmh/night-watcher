package com.tb.nw.core.domain.internal;

import com.tb.nw.core.domain.api.FailoverGroup;
import com.tb.nw.core.domain.api.FencePolicy;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.DomainDef;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.MemberDef;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.VipDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.tb.nw.core.domain.internal.DomainFakes.FakeDomain;
import static com.tb.nw.core.domain.internal.DomainFakes.FakeVip;
import static com.tb.nw.core.domain.internal.DomainFakes.domain;
import static com.tb.nw.core.domain.internal.DomainFakes.member;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainMapperTest {

    @Test void mapsMultiMemberDomainInOrder() {
        DomainDef d = new FakeDomain("btcl-sms", List.of(
                member("freeswitch", 5, "mysql", "sigtran"), member("mysql", 2),
                member("sigtran", 4, "mysql", "redis"), member("redis", 3)),
                Optional.of(new FakeVip("bgp", "10.0.0.1/32")), "SELF",
                "member-odown:mysql", "dell-sms-master", "dell-sms-slave");
        FailoverGroup g = DomainMapper.toGroup(d);
        assertEquals(List.of("mysql", "redis", "sigtran", "freeswitch"),
                g.orderedMembers().stream().map(com.tb.nw.core.domain.api.Member::type).toList());
        assertEquals(FencePolicy.SELF, g.fence());
        assertTrue(g.vip().isPresent());
        assertEquals("bgp", g.vip().get().mode());
    }

    @Test void mapsSingleMemberDomain() {
        FailoverGroup g = DomainMapper.toGroup(
                domain("mysql-only", "node-a", "node-b", List.of(member("mysql", 1))));
        assertTrue(g.isSingleMember());
        assertTrue(g.vip().isEmpty());
        assertEquals("nw-mysql", g.orderedMembers().get(0).pluginId());
    }

    @Test void lowercaseFenceIsAccepted() {
        DomainDef d = new FakeDomain("x", List.of(member("mysql", 1)),
                Optional.empty(), "self", "t", "a", "b");
        assertEquals(FencePolicy.SELF, DomainMapper.toGroup(d).fence());
    }

    @Test void badOrderingRejectedAtMapTime() {
        // mysql(order 2) depends on sigtran(order 5) — dependent promotes before its dependency
        DomainDef d = domain("bad", "a", "b",
                List.of(member("mysql", 2, "sigtran"), member("sigtran", 5)));
        assertThrows(IllegalArgumentException.class, () -> DomainMapper.toGroup(d));
    }
}
