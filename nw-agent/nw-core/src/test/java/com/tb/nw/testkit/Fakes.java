package com.tb.nw.testkit;

import com.tb.nw.core.AgentConfig;
import com.tb.nw.core.coordinator.FailoverConfig;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** Hand impls of the config interfaces + a list-backed CDI Instance. */
public final class Fakes {
    private Fakes() {}

    public static AgentConfig agentConfig(String node, String cluster) {
        return new AgentConfig() {
            @Override public String nodeName() { return node; }
            @Override public String clusterName() { return cluster; }
            @Override public String clusterType() { return "Generic"; }
            @Override public List<String> fabricEndpoints() { return List.of(); }
            @Override public int heartbeatIntervalSec() { return 5; }
            @Override public int heartbeatTtlSec() { return 15; }
            @Override public int probeDeadlineSec() { return 4; }
            @Override public Optional<String> advertiseHost() { return Optional.empty(); }
        };
    }

    public static FailoverConfig failoverConfig(List<String> triggers, boolean requireOdown) {
        return new FailoverConfig() {
            @Override public int failureThreshold() { return 3; }
            @Override public List<String> triggerInvestigators() { return triggers; }
            @Override public int fenceTimeoutSec() { return 10; }
            @Override public int promoteTimeoutSec() { return 10; }
            @Override public int actionDeadlineSec() { return 8; }
            @Override public boolean requireOdown() { return requireOdown; }
            @Override public int resolverFreshnessSec() { return 30; }
            @Override public Optional<String> planPlugin() { return Optional.empty(); }
        };
    }

    /** Same-thread ExecutorService — makes async dispatch synchronous in tests. */
    public static java.util.concurrent.ExecutorService directExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            private volatile boolean down;
            @Override public void execute(Runnable command) { command.run(); }
            @Override public void shutdown() { down = true; }
            @Override public List<Runnable> shutdownNow() { down = true; return List.of(); }
            @Override public boolean isShutdown() { return down; }
            @Override public boolean isTerminated() { return down; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        };
    }

    /** Minimal list-backed Instance<T> — enough for iteration-style consumers. */
    public static <T> Instance<T> instanceOf(List<T> items) {
        return new Instance<>() {
            @Override public Iterator<T> iterator() { return items.iterator(); }
            @Override public T get() { return items.get(0); }
            @Override public Instance<T> select(Annotation... q) { return this; }
            @Override public <U extends T> Instance<U> select(Class<U> c, Annotation... q) {
                throw new UnsupportedOperationException();
            }
            @Override public <U extends T> Instance<U> select(TypeLiteral<U> t, Annotation... q) {
                throw new UnsupportedOperationException();
            }
            @Override public boolean isUnsatisfied() { return items.isEmpty(); }
            @Override public boolean isAmbiguous() { return items.size() > 1; }
            @Override public void destroy(T t) {}
            @Override public Handle<T> getHandle() { throw new UnsupportedOperationException(); }
            @Override public Iterable<? extends Handle<T>> handles() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
