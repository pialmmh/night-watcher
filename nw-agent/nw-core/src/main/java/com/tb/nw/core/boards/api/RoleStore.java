package com.tb.nw.core.boards.api;

import com.tb.nw.core.dependencies.AgentConfig;
import com.tb.nw.core.observe.publishes.FacetPublisher;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricKV;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Roles board — durable (no lease) role per node at
 * {@code /clusters/{c}/roles/{node}}: {@code master} · {@code slave} ·
 * {@code quarantined}.
 *
 * <p>Durability is the point: when a master host dies, its agent's leased
 * keys vanish but its ROLE survives, so the Coordinator still knows who to
 * quarantine and the Selector still knows the slave pool.</p>
 *
 * <p><b>Seeding</b> — each agent observes its own facets ({@code
 * mysql-master-here} / {@code mysql-slave-here} from the live database) and
 * writes its role <em>create-if-absent</em>. A failover's role flips are
 * authoritative and never overwritten by re-seeding.</p>
 */
@Startup
@ApplicationScoped
public class RoleStore {

    private static final Logger LOG = Logger.getLogger(RoleStore.class);

    public static final String MASTER = "master";
    public static final String SLAVE = "slave";
    public static final String QUARANTINED = "quarantined";

    @Inject AgentConfig cfg;
    @Inject Fabric fabric;
    @Inject FacetPublisher facets;

    /** Seed own role from live facets — create-if-absent, every 30 s. */
    @Scheduled(every = "30s", delayed = "10s")
    void seedFromFacets() {
        Optional<String> desired = roleFromFacets(facets.snapshot());
        if (desired.isEmpty()) return;
        try {
            boolean created = fabric.txns().begin()
                    .ifModRevisionEquals(key(cfg.nodeName()), 0)
                    .thenPut(key(cfg.nodeName()), desired.get().getBytes(StandardCharsets.UTF_8))
                    .commit()
                    .succeeded();
            if (created) LOG.infof("role seeded from facets: %s = %s", cfg.nodeName(), desired.get());
        } catch (FabricException e) {
            LOG.debugf("role seed skipped (fabric): %s", e.getMessage());
        }
    }

    /** Authoritative flip — used by the Coordinator during failover. */
    public void put(String node, String role) {
        fabric.kv().put(key(node), role.getBytes(StandardCharsets.UTF_8));
        LOG.infof("roles board: %s → %s", node, role);
    }

    /** Every node's role list (single-element lists — shape matches SelectionContext). */
    public Map<String, List<String>> all() {
        Map<String, List<String>> out = new HashMap<>();
        try {
            for (FabricKV.KeyValue kv : fabric.kv().list(prefix())) {
                String node = kv.key().substring(prefix().length());
                out.put(node, List.of(new String(kv.value(), StandardCharsets.UTF_8)));
            }
        } catch (FabricException e) {
            LOG.warnf("roles board unreadable: %s", e.getMessage());
        }
        return out;
    }

    /** Roles of one node — empty set when unlisted. */
    public Set<String> rolesOf(String node) {
        return Set.copyOf(all().getOrDefault(node, List.of()));
    }

    /**
     * Plugin-agnostic facet convention: any {@code *-master-here} facet
     * claims the master role, any {@code *-slave-here} the slave role
     * (mysql-master-here, mock-master-here, postgres-master-here, …).
     */
    private static Optional<String> roleFromFacets(Set<String> snapshot) {
        boolean master = snapshot.stream().anyMatch(f -> f.endsWith("-master-here"));
        boolean slave = snapshot.stream().anyMatch(f -> f.endsWith("-slave-here"));
        if (master == slave) return Optional.empty();   // neither, or ambiguous mid-promotion
        return Optional.of(master ? MASTER : SLAVE);
    }

    private String prefix() { return "/clusters/" + cfg.clusterName() + "/roles/"; }
    private String key(String node) { return prefix() + node; }
}
