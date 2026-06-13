package com.tb.nw.core.door;

import com.tb.nw.spi.api.CommandResultEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Replay memory for the ActionEndpoint — the last {@value #MAX_ENTRIES}
 * results keyed by command eventId, in-process only.
 *
 * <p>Two properties the stories require:</p>
 * <ul>
 *   <li>A retransmitted command id returns the remembered result — the
 *       action never runs twice for the same id.</li>
 *   <li>Two concurrent arrivals of the same id race on ONE future: the
 *       first executes, the second waits for and returns the same result.</li>
 * </ul>
 *
 * <p>A restarted agent forgets — acceptable because every action's SQL move
 * is itself idempotent (re-checks before changing).</p>
 */
@ApplicationScoped
public class IdempotencyStore {

    private static final Logger LOG = Logger.getLogger(IdempotencyStore.class);
    private static final int MAX_ENTRIES = 1000;

    private final ConcurrentHashMap<String, CompletableFuture<CommandResultEvent>> results =
            new ConcurrentHashMap<>();

    /**
     * Execute-once gate. The first caller for an eventId runs {@code work};
     * every other caller (concurrent or later) receives the same result.
     */
    public CommandResultEvent executeOnce(String eventId, Supplier<CommandResultEvent> work) {
        CompletableFuture<CommandResultEvent> mine = new CompletableFuture<>();
        CompletableFuture<CommandResultEvent> existing = results.putIfAbsent(eventId, mine);
        if (existing != null) {
            LOG.infof("idempotent replay for command %s — returning remembered result", eventId);
            return existing.join();
        }
        try {
            CommandResultEvent result = work.get();
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            // Remember failures too — a replayed command must not re-run a
            // half-broken action; the Coordinator decides on a NEW command.
            mine.completeExceptionally(e);
            results.remove(eventId, mine);
            throw e;
        } finally {
            evictIfOverflowing();
        }
    }

    private void evictIfOverflowing() {
        if (results.size() <= MAX_ENTRIES) return;
        // Cheap pressure valve: drop completed entries until back under cap.
        var it = results.entrySet().iterator();
        while (it.hasNext() && results.size() > MAX_ENTRIES) {
            var e = it.next();
            if (e.getValue().isDone()) it.remove();
        }
    }
}
