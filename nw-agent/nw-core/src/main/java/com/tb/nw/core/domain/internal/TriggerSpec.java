package com.tb.nw.core.domain.internal;

import com.tb.nw.core.domain.api.FailoverGroup;
import com.tb.nw.core.domain.api.Member;
import com.tb.nw.spi.api.Verdict;

import java.util.Optional;
import java.util.function.Function;

/**
 * A parsed domain trigger — the custom, per-group rule for "is the whole group
 * down?". Today one built-in form: {@code member-odown:<type>} (the group fails
 * over when that member's seat vote reaches ODOWN — e.g. the SMS domain's
 * "db down → switch"). New forms (node-heartbeat, quorum) add a case here, or a
 * plugin ships a {@code ClusterCondition} for fully custom logic.
 */
public sealed interface TriggerSpec {

    boolean isMet(FailoverGroup group, Function<Member, Optional<Verdict>> verdictOf);

    static TriggerSpec parse(String condition) {
        if (condition == null || condition.isBlank())
            throw new IllegalArgumentException("empty trigger condition");
        String[] parts = condition.split(":", 2);
        return switch (parts[0]) {
            case "member-odown" -> new MemberOdown(requireArg(parts, condition));
            default -> throw new IllegalArgumentException("unknown trigger condition: " + parts[0]);
        };
    }

    private static String requireArg(String[] parts, String condition) {
        if (parts.length < 2 || parts[1].isBlank())
            throw new IllegalArgumentException("trigger needs a member type: " + condition);
        return parts[1];
    }

    /** Group fails over when the named member's verdict is ODOWN. */
    record MemberOdown(String memberType) implements TriggerSpec {
        @Override
        public boolean isMet(FailoverGroup group, Function<Member, Optional<Verdict>> verdictOf) {
            return group.members().stream()
                    .filter(m -> m.type().equals(memberType))
                    .findFirst()
                    .flatMap(verdictOf)
                    .map(v -> v == Verdict.ODOWN)
                    .orElse(false);
        }
    }
}
