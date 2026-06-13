package com.tb.nw.plugins.mysql.publishes;

import com.tb.nw.spi.api.NwEvent;

/**
 * Marker for every event the {@code nw-mysql} plugin produces or consumes.
 * Categorized further by {@link MySqlHealthEvent}, {@link MySqlCommandEvent},
 * and {@link MySqlCommandResultEvent}.
 *
 * <p>Plugin authors writing additional MySQL-specific event categories
 * (metrics, lifecycle, etc.) extend this marker so all of the plugin's
 * domain traffic sits under a single super-type.</p>
 */
public interface MySqlNwEvent extends NwEvent {}
