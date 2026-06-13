package com.tb.nw.core.domain.api;

import com.tb.nw.core.boards.api.FailoverGate;
import com.tb.nw.core.boards.api.RoleStore;
import com.tb.nw.core.cache.api.ObservationCache;
import com.tb.nw.core.dispatch.api.Dispatcher;
import com.tb.nw.core.domain.internal.GroupFailoverExecutor;
import com.tb.nw.core.vote.api.Resolver;
import com.tb.nw.spi.api.Observation;
import com.tb.nw.spi.api.Verdict;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Group-aware failover entry. Evaluates each declared domain's trigger over the
 * live per-member verdicts; on a domain-down, runs the ordered group failover
 * off the watcher thread. Lazy (not {@code @Startup}) so the domain config
 * binding is exercised only once a domain is actually declared. Sits beside the
 * legacy per-target FailoverCoordinator while the single-service path migrates
 * to one-member domains; it acts only when {@link #evaluateDomains()} or
 * {@link #declareDomainDown} is called, so the two never double-fire.
 */
@ApplicationScoped
public class GroupCoordinator {

    private static final Logger LOG = Logger.getLogger(GroupCoordinator.class);

    @Inject DomainRegistry registry;
    @Inject GroupTrigger trigger;
    @Inject GroupPlanGenerator planGenerator;
    @Inject MemberPlanSource memberPlans;
    @Inject Resolver resolver;
    @Inject ObservationCache cache;
    @Inject FailoverGate gate;
    @Inject RoleStore roles;
    @Inject Dispatcher dispatcher;

    private GroupFailoverExecutor executor;
    private final ExecutorService coordThread =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "nw-group-coord"));

    @PostConstruct
    void init() {
        executor = new GroupFailoverExecutor(gate, roles, planGenerator, memberPlans,
                dispatcher::send, Clock.systemUTC());
        LOG.infof("GroupCoordinator armed — %d domain(s) declared", registry.all().size());
    }

    @PreDestroy
    void shutdown() {
        coordThread.shutdownNow();
    }

    /** Check every declared domain's trigger; fire failover for any that is down. */
    public void evaluateDomains() {
        for (FailoverGroup group : registry.all()) {
            if (trigger.isDomainDown(group, this::verdictForMember)) {
                LOG.infof("domain %s trigger met — failing the group over", group.id());
                declareDomainDown(group, Verdict.ODOWN);
            }
        }
    }

    /** Run a domain failover off the watcher thread. The gate guards re-entry. */
    public void declareDomainDown(FailoverGroup group, Verdict verdict) {
        coordThread.submit(() -> {
            try {
                executor.runFailover(group, verdict);
            } catch (Exception e) {
                LOG.errorf(e, "group failover for %s threw", group.id());
            }
        });
    }

    /** A member's verdict = the worst verdict among the targets its plugin observes. */
    private Optional<Verdict> verdictForMember(Member member) {
        Verdict worst = null;
        for (Observation<?> o : cache.all()) {
            if (!member.pluginId().equals(o.pluginId())) continue;
            Verdict v = resolver.verdictFor(o.target()).orElse(null);
            if (v != null && (worst == null || v.ordinal() > worst.ordinal())) worst = v;
        }
        return Optional.ofNullable(worst);
    }
}
