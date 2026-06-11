package com.tb.nw.core.dispatch;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wire shape of a command crossing agent-to-agent HTTP. Carries the concrete
 * command class name (checked against the plugin descriptor's whitelist —
 * never used for arbitrary class loading) plus the command record as a raw
 * JSON tree, so the endpoint can run the identity gate BEFORE typed parsing.
 */
public record CommandEnvelope(
        String commandClass,
        JsonNode command
) {}
