package com.tb.nw.core.dispatch;

import com.tb.nw.core.PluginRegistry;
import com.tb.nw.core.boards.AgentDirectory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandRefused;
import com.tb.nw.spi.api.CommandResultEvent;
import com.tb.nw.spi.api.PluginDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The Coordinator's messenger — carries ONE command to ONE agent and waits
 * for its typed result on the same call.
 *
 * <p>Behavior per the story: look up the door in the Registry (missing entry
 * = agent gone, no retries) · deliver with up to {@value #MAX_ATTEMPTS}
 * attempts, {@value #RETRY_PAUSE_MS} ms pause, inside the caller's deadline ·
 * the command id never changes across retries · verify the reply's plugin
 * stamp and correlation id · hand back the verdict, never interpret it.</p>
 */
@ApplicationScoped
public class Dispatcher {

    private static final Logger LOG = Logger.getLogger(Dispatcher.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_PAUSE_MS = 1000;

    /** Delivery failed for a stated reason ("agent-unknown", "unreachable", …). */
    public static class DispatchException extends RuntimeException {
        public DispatchException(String reason) { super(reason); }
        public DispatchException(String reason, Throwable cause) { super(reason, cause); }
    }

    @Inject PluginRegistry plugins;
    @Inject AgentDirectory directory;
    @Inject ObjectMapper json;

    /** Package-private so tests can point delivery at a stub server's client. */
    HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * Deliver {@code cmd} to its target node and return the typed result.
     *
     * @throws DispatchException "agent-unknown" when the Registry has no
     *         entry, "unreachable" when every attempt inside the deadline
     *         failed, "bad-reply" when the reply never validated.
     */
    public CommandResultEvent send(CommandEvent cmd, Duration deadline) {
        AgentDirectory.AgentEntry target = directory.lookup(cmd.targetNode())
                .orElseThrow(() -> new DispatchException("agent-unknown"));

        byte[] body = serialize(cmd);
        Instant giveUpAt = Instant.now().plus(deadline);
        Exception last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS && Instant.now().isBefore(giveUpAt); attempt++) {
            try {
                CommandResultEvent result = postAndParse(target.commandUrl(), body, cmd, giveUpAt);
                if (result != null) return result;
                last = new DispatchException("bad-reply");
            } catch (DispatchException e) {
                throw e;
            } catch (Exception e) {
                last = e;
                LOG.warnf("dispatch attempt %d/%d to %s failed: %s",
                        attempt, MAX_ATTEMPTS, cmd.targetNode(), e.getMessage());
            }
            pauseBeforeRetry(giveUpAt);
        }
        throw new DispatchException("unreachable", last);
    }

    // ── one attempt ──

    private CommandResultEvent postAndParse(String url, byte[] body, CommandEvent cmd, Instant giveUpAt)
            throws Exception {
        Duration remaining = Duration.between(Instant.now(), giveUpAt);
        if (remaining.isNegative() || remaining.isZero()) throw new DispatchException("unreachable");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(remaining)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("http " + response.statusCode());
        }
        return validateReply(response.body(), cmd);
    }

    /**
     * Two-phase reply validation — identity first, body second. Accepts the
     * command plugin's declared result types plus the framework's
     * {@link CommandRefused}. A reply answering a different command id, or
     * carrying a stranger's stamp, is dropped (returns null → retry).
     */
    private CommandResultEvent validateReply(byte[] raw, CommandEvent cmd) throws Exception {
        ResultEnvelope envelope = json.readValue(raw, ResultEnvelope.class);
        JsonNode node = envelope.result();

        String pluginId = node.path("pluginId").asText(null);
        String pluginVersion = node.path("pluginVersion").asText(null);
        if (pluginId == null || !plugins.versionMatches(pluginId, pluginVersion)) {
            LOG.warnf("reply dropped: stranger stamp pluginId=%s version=%s", pluginId, pluginVersion);
            return null;
        }

        Class<? extends CommandResultEvent> cls = resolveResultClass(envelope.resultClass(), cmd);
        if (cls == null) {
            LOG.warnf("reply dropped: result class %s not whitelisted", envelope.resultClass());
            return null;
        }

        CommandResultEvent result = json.treeToValue(node, cls);
        if (!cmd.eventId().equals(result.inReplyToEventId())) {
            LOG.warnf("reply dropped: answers %s, expected %s", result.inReplyToEventId(), cmd.eventId());
            return null;
        }
        return result;
    }

    private Class<? extends CommandResultEvent> resolveResultClass(String fqcn, CommandEvent cmd) {
        if (CommandRefused.class.getName().equals(fqcn)) return CommandRefused.class;
        PluginDescriptor d = plugins.byId(cmd.pluginId());
        if (d == null) return null;
        Optional<Class<? extends CommandResultEvent>> cls = plugins.resultClass(d, fqcn);
        return cls.orElse(null);
    }

    // ── plumbing ──

    private byte[] serialize(CommandEvent cmd) {
        try {
            return json.writeValueAsBytes(new CommandEnvelope(cmd.getClass().getName(), json.valueToTree(cmd)));
        } catch (Exception e) {
            throw new DispatchException("serialize-failed", e);
        }
    }

    private static void pauseBeforeRetry(Instant giveUpAt) {
        if (Instant.now().plusMillis(RETRY_PAUSE_MS).isAfter(giveUpAt)) return;
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
