#!/bin/bash
# Watchdog: monitors backend health endpoints and manages nginx upstream failover
# Reads services.conf for list of backends to monitor
# Logs events to MySQL and /var/log/security-bundle/watchdog.log

set -u

# Load MySQL env
[ -f /etc/security-bundle.env ] && source /etc/security-bundle.env

SERVICES_CONF="${SERVICES_CONF:-/opt/security-bundle/scripts/services.conf}"
LOG_FILE="/var/log/security-bundle/watchdog.log"
NGINX_UPSTREAM_DIR="/etc/nginx/conf.d"
STATE_DIR="/var/run/watchdog"

mkdir -p "$STATE_DIR" "$(dirname "$LOG_FILE")"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') [$1] $2" >> "$LOG_FILE"
    echo "$(date '+%Y-%m-%d %H:%M:%S') [$1] $2"
}

mysql_log() {
    local service="$1" event_type="$2" detail="$3"
    if [ -n "${MYSQL_HOST:-}" ] && [ -n "${MYSQL_PASS:-}" ]; then
        mysql -h "$MYSQL_HOST" -P "${MYSQL_PORT:-3306}" -u "$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" \
            -e "INSERT INTO watchdog_events (tenant, service_name, event_type, detail, created_at) VALUES ('${TENANT_NAME:-unknown}', '$service', '$event_type', '$detail', NOW())" \
            2>/dev/null || true
    fi
}

check_health() {
    local url="$1" timeout="${2:-5}"
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time "$timeout" "$url" 2>/dev/null)
    [ "$http_code" = "200" ] && return 0
    return 1
}

swap_upstream() {
    local service_name="$1" from_addr="$2" to_addr="$3"
    local upstream_file="$NGINX_UPSTREAM_DIR/upstream-${service_name}.conf"

    if [ ! -f "$upstream_file" ]; then
        log "WARN" "No upstream file for $service_name at $upstream_file"
        return 1
    fi

    log "INFO" "Swapping $service_name upstream: $from_addr -> $to_addr"

    # Replace server address in upstream block
    sed -i "s|server $from_addr;|server $to_addr;|g" "$upstream_file"

    # Reload nginx
    nginx -t 2>/dev/null && nginx -s reload 2>/dev/null
    return $?
}

drain_and_swap() {
    local service_name="$1" primary="$2" standby="$3" drain_wait="$4"

    log "INFO" "$service_name: primary ($primary) unhealthy, draining for ${drain_wait}s before failover"
    mysql_log "$service_name" "drain_start" "Primary $primary unhealthy, draining ${drain_wait}s"

    sleep "$drain_wait"

    # Re-check primary after drain period
    if check_health "http://$primary/health"; then
        log "INFO" "$service_name: primary recovered during drain, no failover needed"
        mysql_log "$service_name" "drain_recovered" "Primary $primary recovered during drain"
        return 0
    fi

    # Failover to standby
    if [ -n "$standby" ]; then
        if check_health "http://$standby/health"; then
            swap_upstream "$service_name" "$primary" "$standby"
            log "WARN" "$service_name: FAILOVER to standby $standby"
            mysql_log "$service_name" "failover" "Switched from $primary to $standby"
            echo "$standby" > "$STATE_DIR/${service_name}.active"
        else
            log "ERROR" "$service_name: BOTH primary and standby unhealthy!"
            mysql_log "$service_name" "both_down" "Primary $primary and standby $standby both unhealthy"
        fi
    else
        log "ERROR" "$service_name: primary unhealthy, no standby configured"
        mysql_log "$service_name" "primary_down" "Primary $primary unhealthy, no standby"
    fi
}

# ── Main loop ────────────────────────────────────────────────────────────────

if [ ! -f "$SERVICES_CONF" ]; then
    log "ERROR" "Services config not found: $SERVICES_CONF"
    log "INFO" "Watchdog running in idle mode (no services to monitor)"
    while true; do sleep 60; done
fi

log "INFO" "Watchdog starting, config: $SERVICES_CONF"

while true; do
    while IFS='|' read -r name health_url primary standby interval drain_wait; do
        # Skip comments and empty lines
        [[ "$name" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$name" ]] && continue

        name=$(echo "$name" | xargs)
        health_url=$(echo "$health_url" | xargs)
        primary=$(echo "$primary" | xargs)
        standby=$(echo "$standby" | xargs)
        interval=$(echo "$interval" | xargs)
        drain_wait=$(echo "$drain_wait" | xargs)

        # Get current active backend
        current_active="$primary"
        if [ -f "$STATE_DIR/${name}.active" ]; then
            current_active=$(cat "$STATE_DIR/${name}.active")
        fi

        if check_health "$health_url"; then
            # If we're on standby and primary recovers, swap back
            if [ "$current_active" != "$primary" ]; then
                if check_health "http://$primary/health"; then
                    swap_upstream "$name" "$current_active" "$primary"
                    log "INFO" "$name: primary recovered, swapping back to $primary"
                    mysql_log "$name" "failback" "Primary $primary recovered, switching back from $current_active"
                    echo "$primary" > "$STATE_DIR/${name}.active"
                fi
            fi

            # Reset failure counter
            rm -f "$STATE_DIR/${name}.failures"
        else
            # Track consecutive failures
            failures=$(cat "$STATE_DIR/${name}.failures" 2>/dev/null || echo 0)
            failures=$((failures + 1))
            echo "$failures" > "$STATE_DIR/${name}.failures"

            if [ "$failures" -ge 3 ] && [ "$current_active" = "$primary" ]; then
                # 3 consecutive failures — initiate drain and swap
                drain_and_swap "$name" "$primary" "$standby" "${drain_wait:-30}" &
                echo 0 > "$STATE_DIR/${name}.failures"
            else
                log "WARN" "$name: health check failed ($failures consecutive)"
            fi
        fi

    done < "$SERVICES_CONF"

    # Sleep for the shortest interval among services (default 5s)
    sleep "${MIN_INTERVAL:-5}"
done
