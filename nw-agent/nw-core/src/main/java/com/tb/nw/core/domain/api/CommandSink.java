package com.tb.nw.core.domain.api;

import com.tb.nw.spi.api.CommandEvent;
import com.tb.nw.spi.api.CommandResultEvent;

import java.time.Duration;

/**
 * Where the group executor sends a command — the seam over the Dispatcher.
 * Production wires {@code dispatcher::send}; tests record the sends and feed
 * back canned results. Keeps the executor free of HTTP for unit testing.
 */
@FunctionalInterface
public interface CommandSink {
    CommandResultEvent send(CommandEvent command, Duration budget) throws Exception;
}
