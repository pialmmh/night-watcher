package com.tb.nw.core.domain.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The unit of failover — always a list of members, even for a single service.
 * A length-1 list is the base case, not a special case: the orchestrator walks
 * {@link #orderedMembers()} the same way for one member or five. There is no
 * single-service code path.
 */
public record FailoverGroup(
        String id,
        List<Member> members,
        Optional<Vip> vip,
        FencePolicy fence,
        String triggerCondition,   // e.g. "member-odown:mysql"
        String activeNode,
        String standbyNode
) {
    public FailoverGroup {
        if (members == null || members.isEmpty())
            throw new IllegalArgumentException("a failover group needs at least one member");
        members = List.copyOf(members);
        vip = vip == null ? Optional.empty() : vip;
    }

    /** Members in promote order (ascending {@code order}) — the uniform walk. */
    public List<Member> orderedMembers() {
        return members.stream().sorted(Comparator.comparingInt(Member::order)).toList();
    }

    public boolean isSingleMember() {
        return members.size() == 1;
    }

    /** Every {@code dependsOn} type must exist in the group and promote earlier. */
    public void validateOrdering() {
        Map<String, Integer> orderByType = members.stream()
                .collect(Collectors.toMap(Member::type, Member::order, (a, b) -> a));
        for (Member m : members) {
            for (String dep : m.dependsOn()) {
                Integer depOrder = orderByType.get(dep);
                if (depOrder == null)
                    throw new IllegalArgumentException(m.type() + " depends on unknown member " + dep);
                if (depOrder >= m.order())
                    throw new IllegalArgumentException(m.type() + " must promote after " + dep);
            }
        }
    }
}
