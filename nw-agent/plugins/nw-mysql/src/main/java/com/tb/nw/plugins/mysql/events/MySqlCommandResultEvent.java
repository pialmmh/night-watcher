package com.tb.nw.plugins.mysql.events;

import com.tb.nw.spi.CommandResultEvent;

/** Marker for every result a {@code nw-mysql} action returns to the Dispatcher. */
public interface MySqlCommandResultEvent extends MySqlNwEvent, CommandResultEvent {}
