package com.tb.nw.core.domain.internal;

import com.tb.nw.core.cache.api.ObservationCache;
import com.tb.nw.core.dependencies.AgentConfig;
import com.tb.nw.core.boards.api.RoleStore;
import com.tb.nw.core.domain.api.Member;
import com.tb.nw.core.domain.api.MemberPlanSource;
import com.tb.nw.spi.api.ClusterType;
import com.tb.nw.spi.api.ClusterView;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.FailoverPlanGenerator;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Verdict;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The production {@link MemberPlanSource}: for one member, look up its plugin's
 * {@link FailoverPlanGenerator} by plugin id, build the {@link ClusterView} from
 * the live roles + that plugin's observations, and ask it for the member's
 * activation sub-plan. This is the per-member version of what the legacy
 * FailoverCoordinator does for a single target.
 */
@ApplicationScoped
public class PluginMemberPlanSource implements MemberPlanSource {

    private static final Logger LOG = Logger.getLogger(PluginMemberPlanSource.class);

    @Inject Instance<FailoverPlanGenerator<?, ?>> generators;
    @Inject AgentConfig agentCfg;
    @Inject RoleStore roles;
    @Inject ObservationCache cache;

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Optional<Plan<? extends CommandEvent>> planFor(Member member, Verdict verdict, long failoverEpoch) {
        FailoverPlanGenerator generator = generatorFor(member.pluginId());
        if (generator == null) {
            LOG.warnf("no FailoverPlanGenerator for member %s (plugin %s) — skipped",
                    member.type(), member.pluginId());
            return Optional.empty();
        }
        ClusterView view = new ClusterView(
                agentCfg.clusterName(),
                ClusterType.parse(agentCfg.clusterType()).orElse(ClusterType.GENERIC),
                failoverEpoch,
                roles.all(),
                observationsForPlugin(member.pluginId()));
        Plan<?> sub = generator.generate(verdict, view);
        return Optional.ofNullable(sub);
    }

    private FailoverPlanGenerator<?, ?> generatorFor(String pluginId) {
        for (FailoverPlanGenerator<?, ?> g : generators) {
            if (pluginId.equals(g.descriptor().pluginId())) return g;
        }
        return null;
    }

    private List<Observation<?>> observationsForPlugin(String pluginId) {
        List<Observation<?>> out = new ArrayList<>();
        for (Observation<?> o : cache.all()) {
            if (pluginId.equals(o.pluginId())) out.add(o);
        }
        return out;
    }
}
