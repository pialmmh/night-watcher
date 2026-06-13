package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.spi.api.CommandEvent;

/**
 * Marker for every {@link CommandEvent} the orchestrator sends to a
 * {@code nw-mysql} action on a target node.
 *
 * <p>Concrete commands live as records implementing this interface — see
 * {@link MySqlPromoteSlaveCommand}, {@link MySqlFenceMasterCommand},
 * {@link MySqlSetReadOnlyCommand}, {@link MySqlStartReplicaCommand},
 * {@link MySqlStopReplicaCommand}.</p>
 */
public interface MySqlCommandEvent extends MySqlNwEvent, CommandEvent {}
