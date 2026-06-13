package com.tb.nw.core.domain.internal;

import com.tb.nw.core.domain.api.CommandSink;
import com.tb.nw.spi.api.ClusterView;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.FailoverPlanGenerator;
import com.tb.nw.spi.api.HealthCheckEvent;
import com.tb.nw.spi.api.NwEvent;
import com.tb.nw.spi.api.Plan;
import com.tb.nw.spi.api.Plan.PlanStep;
import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.spi.api.Verdict;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Shared doubles for the group runtime tests. */
public final class GroupRuntimeFakes {

    private GroupRuntimeFakes() {}

    public record FakeCommand(String pluginId, String pluginVersion, String eventId, Instant timestamp,
                              String commandKind, String targetNode, long failoverEpoch) implements CommandEvent {}

    public record FakeResult(String pluginId, String pluginVersion, String eventId, Instant timestamp,
                             String inReplyToEventId, boolean ok, String reason) implements CommandResultEvent {}

    public record FakeDescriptor(String pluginId) implements PluginDescriptor {
        @Override public String pluginVersion() { return "1.0.0"; }
        @Override public String serviceType() { return "test"; }
        @Override public Class<? extends HealthCheckEvent> healthEventType() { return null; }
        @Override public java.util.Set<Class<? extends CommandEvent>> commandEventTypes() { return java.util.Set.of(); }
    }

    public static CommandEvent cmd(String kind, String node, long epoch) {
        return new FakeCommand("nw-" + kind, "1.0.0", NwEvent.newEventId(), Instant.EPOCH, kind, node, epoch);
    }

    public static PlanStep<CommandEvent> step(String kind, String desc, String node, long epoch) {
        return new PlanStep<>(0, "ACTION_CALL", desc, cmd(kind, node, epoch), 1000, 0);
    }

    @SafeVarargs
    public static Plan<CommandEvent> subPlan(long epoch, PlanStep<CommandEvent>... steps) {
        return new Plan<>("sub", "c", epoch, Instant.EPOCH, List.of(steps), Map.of());
    }

    /** A FailoverPlanGenerator returning a fixed sub-plan; records the epoch it saw. */
    public static final class FakeGenerator implements FailoverPlanGenerator<HealthCheckEvent, CommandEvent> {
        private final PluginDescriptor descriptor;
        private final List<PlanStep<CommandEvent>> steps;
        public long sawEpoch = -1;

        public FakeGenerator(String pluginId, List<PlanStep<CommandEvent>> steps) {
            this.descriptor = new FakeDescriptor(pluginId);
            this.steps = steps;
        }

        @Override public PluginDescriptor descriptor() { return descriptor; }

        @Override public Plan<CommandEvent> generate(Verdict verdict, ClusterView<HealthCheckEvent> view) {
            sawEpoch = view.failoverEpoch();
            return new Plan<>("sub", view.cluster(), view.failoverEpoch(), Instant.EPOCH, steps, Map.of());
        }
    }

    /** Records every send; oks/refuses/throws per the given predicates on the command. */
    public static final class RecordingSink implements CommandSink {
        public final List<CommandEvent> sent = new ArrayList<>();
        private final Predicate<CommandEvent> okIf;
        private final Predicate<CommandEvent> throwIf;

        public RecordingSink(Predicate<CommandEvent> okIf, Predicate<CommandEvent> throwIf) {
            this.okIf = okIf;
            this.throwIf = throwIf;
        }

        public static RecordingSink allOk() {
            return new RecordingSink(c -> true, c -> false);
        }

        @Override public CommandResultEvent send(CommandEvent command, Duration budget) throws Exception {
            sent.add(command);
            if (throwIf.test(command)) throw new RuntimeException("unreachable: " + command.targetNode());
            boolean ok = okIf.test(command);
            return new FakeResult("nw", "1.0.0", NwEvent.newEventId(), Instant.EPOCH,
                    command.eventId(), ok, ok ? "done" : "refused");
        }
    }
}
