package com.tb.nw.core.domain.api;

import com.tb.nw.core.domain.dependencies.FailoverDomainConfig;
import com.tb.nw.core.domain.internal.DomainMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the declared failover domains from config and answers which group a
 * node belongs to. The orchestrator reads {@link #domainOf(String)} to find the
 * {@link FailoverGroup} (always a list of members) it must walk on failover.
 */
@ApplicationScoped
public class DomainRegistry {

    @Inject FailoverDomainConfig config;

    private final Map<String, FailoverGroup> byId = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        config.domains().orElse(List.of()).forEach(d -> {
            FailoverGroup group = DomainMapper.toGroup(d);
            byId.put(group.id(), group);
        });
    }

    public Collection<FailoverGroup> all() {
        return List.copyOf(byId.values());
    }

    public Optional<FailoverGroup> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The domain a node serves in — whether it is the active or the standby side. */
    public Optional<FailoverGroup> domainOf(String node) {
        return byId.values().stream()
                .filter(g -> node.equals(g.activeNode()) || node.equals(g.standbyNode()))
                .findFirst();
    }
}
