package com.tb.nw.core.domain.api;

/** The result of one domain failover attempt. */
public record GroupFailoverOutcome(Status status, String reason, String newActiveNode) {

    public enum Status { DONE, FAILED, STOOD_DOWN }

    public static GroupFailoverOutcome done(String newActiveNode) {
        return new GroupFailoverOutcome(Status.DONE, "ok", newActiveNode);
    }

    public static GroupFailoverOutcome failed(String reason) {
        return new GroupFailoverOutcome(Status.FAILED, reason, null);
    }

    public static GroupFailoverOutcome stoodDown() {
        return new GroupFailoverOutcome(Status.STOOD_DOWN, "another agent leads this failover", null);
    }

    public boolean ok() {
        return status == Status.DONE;
    }
}
