package com.tb.nw.spi.api;

import java.util.Optional;

/**
 * Long-running {@link FailoverAction} whose partial state survives an agent
 * restart. The state-machine library snapshots {@link #snapshotState()} to
 * etcd at every transition; on resume, the agent calls
 * {@link #resumeFromState(ActionContext, String)} to rebuild.
 *
 * <p>Most actions complete fast enough that plain {@link FailoverAction}
 * with idempotency-token replay is the right choice — use this only when a
 * step takes minutes (e.g. {@code mysql.basebackup-from}) and retry-from-zero
 * would be expensive.</p>
 *
 * @param <P> plugin-specific command event
 */
public interface StatefulAction<P extends CommandEvent> extends FailoverAction<P> {

    /** Called when the action's state was previously persisted via {@link #snapshotState()}. */
    Optional<CommandResultEvent> resumeFromState(ActionContext<P> ctx, String persistedState);

    /** Serialize current progress so a future resume can pick up where we left off. */
    String snapshotState();
}
