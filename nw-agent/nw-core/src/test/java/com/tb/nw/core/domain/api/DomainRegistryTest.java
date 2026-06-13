package com.tb.nw.core.domain.api;

import com.tb.nw.core.domain.dependencies.FailoverDomainConfig;
import com.tb.nw.core.domain.dependencies.FailoverDomainConfig.DomainDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.tb.nw.core.domain.internal.DomainFakes.domain;
import static com.tb.nw.core.domain.internal.DomainFakes.member;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRegistryTest {

    private record FakeConfig(Optional<List<DomainDef>> domains) implements FailoverDomainConfig {}

    private static DomainRegistry registryWith(DomainDef... domains) {
        DomainRegistry r = new DomainRegistry();
        r.config = new FakeConfig(Optional.of(List.of(domains)));
        r.load();
        return r;
    }

    @Test void resolvesDomainByEitherNode() {
        DomainRegistry r = registryWith(
                domain("btcl-sms", "dell-sms-master", "dell-sms-slave",
                        List.of(member("mysql", 2), member("redis", 3))),
                domain("mysql-only", "node-a", "node-b", List.of(member("mysql", 1))));
        assertEquals(2, r.all().size());
        assertEquals("btcl-sms", r.domainOf("dell-sms-master").orElseThrow().id());
        assertEquals("btcl-sms", r.domainOf("dell-sms-slave").orElseThrow().id());
        assertEquals("mysql-only", r.domainOf("node-b").orElseThrow().id());
        assertTrue(r.domainOf("stranger").isEmpty());
    }

    @Test void byIdLooksUpDeclaredDomains() {
        DomainRegistry r = registryWith(
                domain("mysql-only", "node-a", "node-b", List.of(member("mysql", 1))));
        assertTrue(r.byId("mysql-only").isPresent());
        assertTrue(r.byId("ghost").isEmpty());
    }

    @Test void noDomainsConfiguredIsEmptyNotError() {
        DomainRegistry r = new DomainRegistry();
        r.config = new FakeConfig(Optional.empty());
        r.load();
        assertTrue(r.all().isEmpty());
        assertFalse(r.domainOf("anything").isPresent());
    }
}
