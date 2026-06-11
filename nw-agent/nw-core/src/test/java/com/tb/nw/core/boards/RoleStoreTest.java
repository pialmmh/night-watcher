package com.tb.nw.core.boards;

import com.tb.nw.core.observe.FacetPublisher;
import com.tb.nw.testkit.FakeFabric;
import com.tb.nw.testkit.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleStoreTest {

    /** Facet snapshot the test controls. */
    static class StubFacets extends FacetPublisher {
        Set<String> facets = new LinkedHashSet<>();
        @Override public Set<String> snapshot() { return facets; }
    }

    FakeFabric fabric;
    StubFacets facets;
    RoleStore store;

    @BeforeEach void setUp() {
        fabric = new FakeFabric();
        facets = new StubFacets();
        store = new RoleStore();
        store.cfg = Fakes.agentConfig("node-a", "c1");
        store.fabric = fabric;
        store.facets = facets;
    }

    @Test void seedCreatesRoleFromFacets() {
        facets.facets = Set.of("mysql-master-here");
        store.seedFromFacets();
        assertEquals(List.of("master"), store.all().get("node-a"));
    }

    @Test void facetConventionIsPluginAgnostic() {
        facets.facets = Set.of("mock-slave-here");
        store.seedFromFacets();
        assertEquals(List.of("slave"), store.all().get("node-a"));
    }

    @Test void seedNeverOverwritesAFailoverFlip() {
        facets.facets = Set.of("mysql-master-here");
        store.seedFromFacets();
        store.put("node-a", RoleStore.QUARANTINED);    // the coordinator's authoritative flip
        store.seedFromFacets();                        // re-seed must NOT resurrect "master"
        assertEquals(List.of(RoleStore.QUARANTINED), store.all().get("node-a"));
    }

    @Test void ambiguousFacets_noSeed() {
        facets.facets = Set.of("mysql-master-here", "mysql-slave-here");   // mid-promotion
        store.seedFromFacets();
        assertTrue(store.all().isEmpty());
    }

    @Test void noRoleFacets_noSeed() {
        facets.facets = Set.of("mysql-client-here", "witness");
        store.seedFromFacets();
        assertTrue(store.all().isEmpty());
    }

    @Test void rolesOfUnlistedNode_isEmpty() {
        assertTrue(store.rolesOf("ghost").isEmpty());
    }
}
