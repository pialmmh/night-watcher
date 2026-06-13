package com.tb.nw.core.domain.api;

import com.tb.nw.core.domain.internal.TriggerSpec;
import com.tb.nw.spi.api.Verdict;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.function.Function;

/**
 * Decides whether a whole domain should fail over, by its declared trigger.
 * Uniform across single- and multi-member groups: the trigger names which
 * member's verdict (or which cluster condition) flips the whole group.
 */
@ApplicationScoped
public class GroupTrigger {

    /**
     * @param verdictOf resolves each member to its current Resolver verdict
     *                  (empty = no fresh evidence for that member)
     */
    public boolean isDomainDown(FailoverGroup group, Function<Member, Optional<Verdict>> verdictOf) {
        return TriggerSpec.parse(group.triggerCondition()).isMet(group, verdictOf);
    }
}
