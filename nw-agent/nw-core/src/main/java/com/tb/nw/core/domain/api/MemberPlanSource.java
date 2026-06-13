package com.tb.nw.core.domain.api;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Verdict;

import java.util.Optional;

/**
 * The seam between the group orchestrator and a member's plugin: produce the
 * activation sub-plan for one member (its {@code FailoverPlanGenerator} output —
 * e.g. mysql's fence-master + promote-slave steps). Empty = the member supplies
 * no steps and is skipped. This is the contract a member plugin (mysql, redis,
 * sigtran, …) satisfies through its existing SPI; the core composes the list.
 */
@FunctionalInterface
public interface MemberPlanSource {
    /** @param failoverEpoch the group's epoch, stamped into the member's commands */
    Optional<Plan<? extends CommandEvent>> planFor(Member member, Verdict verdict, long failoverEpoch);
}
