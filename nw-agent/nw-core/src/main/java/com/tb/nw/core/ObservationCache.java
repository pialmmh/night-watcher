package com.tb.nw.core;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.fabric.api.FabricKV;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.ObservationView;
import com.tb.nw.spi.PluginDescriptor;
import com.tb.nw.spi.PluginEntity;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Watch-based in-memory mirror of {@code /clusters/{cluster}/observations/}.
 *
 * <p>Every agent — coordinator and follower-shadow alike — maintains one of
 * these so it has the complete evidence set for the cluster without
 * round-tripping to etcd. The Resolver reads from this; the dashboard reads
 * from this; future cross-checks read from this.</p>
 *
 * <h2>Strict plugin-version enforcement</h2>
 *
 * <p>Every inbound observation is split into two phases at deserialization:</p>
 * <ol>
 *   <li>Parse the JSON envelope as a tree; extract {@code pluginId} +
 *       {@code pluginVersion}.</li>
 *   <li>Look up the locally loaded {@link PluginDescriptor} keyed by
 *       {@code pluginId}.
 *     <ul>
 *       <li>Unknown plugin id → drop, log {@code unknown-plugin}.</li>
 *       <li>Version mismatch → drop, log {@code version-mismatch}.</li>
 *       <li>Matched → resolve {@code detailType()}, deserialize the typed
 *           {@code Observation<T>} via Jackson, insert into cache, notify listeners.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Nothing downstream (FailureTracker, FailoverCoordinator) ever sees an
 * observation whose plugin id or version doesn't match a registered plugin.</p>
 */
@Startup
@ApplicationScoped
public class ObservationCache {

    private static final Logger LOG = Logger.getLogger(ObservationCache.class);

    // /clusters/{cluster}/observations/{target}/{publisher}/{investigator}
    private static final Pattern OBSERVATION_KEY =
            Pattern.compile("^/clusters/([^/]+)/observations/([^/]+)/([^/]+)/([^/]+)$");

    @Inject AgentConfig cfg;
    @Inject Fabric fabric;
    @Inject ObjectMapper json;
    @Inject Instance<PluginDescriptor> descriptors;

    private final ConcurrentHashMap<ObsKey, Observation<?>> current = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    /** pluginId → descriptor (lazily resolved once at startup). */
    private final Map<String, PluginDescriptor> descriptorsById = new HashMap<>();

    private FabricKV.Watch watchHandle;

    public record ObsKey(String target, String publisher, String investigator) {}

    public record ChangeEvent(Kind kind, ObsKey key, Observation<?> observation) {
        public enum Kind { ADDED, UPDATED, REMOVED }
    }

    @PostConstruct
    void start() {
        for (PluginDescriptor d : descriptors) {
            descriptorsById.put(d.pluginId(), d);
        }
        LOG.infof("ObservationCache: %d plugin descriptors registered: %s",
                descriptorsById.size(), descriptorsById.keySet());

        String prefix = observationsPrefix();
        try {
            seedFromRangeRead(prefix);
            this.watchHandle = fabric.kv().watch(prefix, this::onEvent);
            LOG.infof("ObservationCache watching %s — seeded with %d entries",
                    prefix, current.size());
        } catch (FabricException e) {
            LOG.warnf("ObservationCache could not start (will retry passively): %s", e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        FabricKV.Watch w = this.watchHandle;
        if (w != null) {
            try { w.close(); } catch (Exception e) {
                LOG.debugf("watch close error: %s", e.getMessage());
            }
        }
        current.clear();
    }

    /** Every observation in the cache. */
    public Collection<Observation<?>> all() {
        return List.copyOf(current.values());
    }

    /** Observations whose {@code target} matches the supplied node id. */
    public List<Observation<?>> byTarget(String target) {
        List<Observation<?>> out = new ArrayList<>();
        for (Map.Entry<ObsKey, Observation<?>> e : current.entrySet()) {
            if (e.getKey().target().equals(target)) out.add(e.getValue());
        }
        return out;
    }

    /** Observations published by the supplied node. */
    public List<Observation<?>> byPublisher(String publisher) {
        List<Observation<?>> out = new ArrayList<>();
        for (Map.Entry<ObsKey, Observation<?>> e : current.entrySet()) {
            if (e.getKey().publisher().equals(publisher)) out.add(e.getValue());
        }
        return out;
    }

    /**
     * Plugin-facing slice for callers that know the typed detail. Returns the
     * cache's observations for {@code target}, filtered + cast to the
     * requested detail type. Callers supplying a {@code detailType} that
     * doesn't match the observations' actual type for that target receive
     * an empty list rather than a {@code ClassCastException}.
     */
    @SuppressWarnings("unchecked")
    public <T extends PluginEntity> ObservationView<T> viewOf(String target, Class<T> detailType) {
        List<Observation<T>> filtered = new ArrayList<>();
        for (Observation<?> obs : byTarget(target)) {
            if (detailType.isInstance(obs.detail())) {
                filtered.add((Observation<T>) obs);
            }
        }
        return new ObservationView<>(target, filtered);
    }

    /** Total entries in cache — quick stat for the status endpoint. */
    public int size() { return current.size(); }

    /** Subscribe to cache change events. Listener is called on the watcher thread. */
    public void addListener(Consumer<ChangeEvent> listener) {
        listeners.add(listener);
    }

    // ── internals ──

    private void seedFromRangeRead(String prefix) {
        for (FabricKV.KeyValue kv : fabric.kv().list(prefix)) {
            ObsKey key = parseKey(kv.key());
            if (key == null) continue;
            Observation<?> obs = deserialize(kv.value());
            if (obs == null) continue;
            current.put(key, obs);
        }
    }

    private void onEvent(FabricKV.KeyEvent ev) {
        ObsKey key = parseKey(ev.key());
        if (key == null) {
            LOG.debugf("ignoring event for non-observation key: %s", ev.key());
            return;
        }
        if (ev.type() == FabricKV.EventType.DELETE) {
            Observation<?> removed = current.remove(key);
            if (removed != null) fire(new ChangeEvent(ChangeEvent.Kind.REMOVED, key, removed));
            return;
        }
        Observation<?> obs = deserialize(ev.value());
        if (obs == null) return;
        Observation<?> prev = current.put(key, obs);
        fire(new ChangeEvent(
                prev == null ? ChangeEvent.Kind.ADDED : ChangeEvent.Kind.UPDATED,
                key, obs));
    }

    private void fire(ChangeEvent ev) {
        for (Consumer<ChangeEvent> l : listeners) {
            try { l.accept(ev); } catch (Exception e) {
                LOG.warnf(e, "observation listener threw");
            }
        }
    }

    private ObsKey parseKey(String key) {
        var m = OBSERVATION_KEY.matcher(key);
        if (!m.matches()) return null;
        return new ObsKey(m.group(2), m.group(3), m.group(4));
    }

    /**
     * Two-phase deserialization with strict plugin-version enforcement.
     *
     * <p>Phase 1: parse JSON tree, read {@code pluginId} + {@code pluginVersion}.
     * Phase 2: look up the registered descriptor for that plugin id, verify the
     * version matches, then deserialize the body using
     * {@code descriptor.observationDetailType()} as the type parameter.</p>
     *
     * <p>Returns {@code null} (and logs a structured warning) when the envelope
     * is malformed, the plugin id isn't registered locally, or the version
     * doesn't match the registered plugin.</p>
     */
    private Observation<?> deserialize(byte[] value) {
        JsonNode node;
        try {
            node = json.readTree(value);
        } catch (Exception e) {
            LOG.warnf("observation parse failed (%d bytes): %s", value.length, e.getMessage());
            return null;
        }

        String pluginId      = node.path("pluginId").asText(null);
        String pluginVersion = node.path("pluginVersion").asText(null);
        if (pluginId == null || pluginVersion == null) {
            LOG.warnf("observation envelope missing pluginId/pluginVersion — dropped (%d bytes)", value.length);
            return null;
        }

        PluginDescriptor descriptor = descriptorsById.get(pluginId);
        if (descriptor == null) {
            LOG.warnf("observation rejected: unknown-plugin pluginId=%s pluginVersion=%s", pluginId, pluginVersion);
            return null;
        }
        if (!descriptor.pluginVersion().equals(pluginVersion)) {
            LOG.warnf("observation rejected: version-mismatch pluginId=%s expected=%s actual=%s",
                    pluginId, descriptor.pluginVersion(), pluginVersion);
            return null;
        }

        try {
            JavaType type = json.getTypeFactory()
                    .constructParametricType(Observation.class, descriptor.observationDetailType());
            return json.convertValue(node, type);
        } catch (Exception e) {
            LOG.warnf("observation typed-deserialize failed for plugin %s@%s: %s",
                    pluginId, pluginVersion, e.getMessage());
            return null;
        }
    }

    private String observationsPrefix() {
        return "/clusters/" + cfg.clusterName() + "/observations/";
    }
}
