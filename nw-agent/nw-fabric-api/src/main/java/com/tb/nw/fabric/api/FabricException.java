package com.tb.nw.fabric.api;

/** Top-level exception for fabric operations. Plugins and core code catch this, not jetcd specifics. */
public class FabricException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,         // fabric unreachable / network
        PRECONDITION_FAILED, // CAS lost
        NOT_FOUND,
        TIMEOUT,
        UNAUTHORIZED,
        INTERNAL
    }

    private final Kind kind;

    public FabricException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public FabricException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
