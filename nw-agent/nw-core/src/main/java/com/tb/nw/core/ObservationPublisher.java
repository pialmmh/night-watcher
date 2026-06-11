package com.tb.nw.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tb.nw.fabric.api.Fabric;
import com.tb.nw.fabric.api.FabricException;
import com.tb.nw.spi.Observation;
import com.tb.nw.spi.HealthCheckEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializes typed Observations as JSON and writes them to etcd under the
 * shared agent lease. Stale entries age out automatically when the agent dies.
 *
 * <p>Holds typed envelopes as {@code Observation<?>} — the publisher itself
 * is plugin-agnostic; serialization preserves the typed detail's shape.</p>
 *
 * <p>Also keeps an in-memory map of the latest observation per investigator
 * published by THIS node, so the status endpoint can show what we sent
 * without re-reading from etcd.</p>
 */
@ApplicationScoped
public class ObservationPublisher {

    private static final Logger LOG = Logger.getLogger(ObservationPublisher.class);

    @Inject Fabric fabric;
    @Inject AgentLease lease;
    @Inject ObjectMapper json;

    private final Map<String, Observation<?>> lastByInvestigator = new ConcurrentHashMap<>();

    public <T extends HealthCheckEvent> void publish(Observation<T> o) {
        try {
            String key = pathFor(o);
            byte[] body = json.writeValueAsBytes(o);
            fabric.kv().put(key, body, lease.current());
            lastByInvestigator.put(o.investigator(), o);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "observation serialize failed: %s", o.investigator());
        } catch (FabricException e) {
            LOG.warnf("observation put failed (%s): %s", o.investigator(), e.getMessage());
        }
    }

    public Map<String, Observation<?>> snapshot() {
        return Map.copyOf(lastByInvestigator);
    }

    private static String pathFor(Observation<?> o) {
        return "/clusters/" + o.cluster()
                + "/observations/" + o.target()
                + "/" + o.publisher()
                + "/" + o.investigator();
    }
}
