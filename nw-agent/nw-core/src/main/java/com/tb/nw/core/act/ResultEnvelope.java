package com.tb.nw.core.act;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wire shape of a command result returning over agent-to-agent HTTP.
 * Mirror of {@link CommandEnvelope}: class name (whitelisted via the plugin
 * descriptor's {@code resultEventTypes()} or the framework's
 * {@code CommandRefused}) + the result record as a raw JSON tree.
 */
public record ResultEnvelope(
        String resultClass,
        JsonNode result
) {}
