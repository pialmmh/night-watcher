package com.tb.nw.plugins.mysql.events;

import com.tb.nw.spi.api.HealthCheckEvent;

/** Marker for every {@link HealthCheckEvent} the {@code nw-mysql} plugin emits. */
public interface MySqlHealthEvent extends MySqlNwEvent, HealthCheckEvent {}
