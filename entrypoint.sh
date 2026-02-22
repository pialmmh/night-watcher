#!/bin/bash
set -e

# Security Bundle entrypoint
# Reads tenant config, wires all components, starts supervisord

TENANT_CONFIG="${TENANT_CONFIG:-/config/tenant.conf}"
COMMON_DIR="/opt/security-bundle"
LOG_DIR="/var/log/security-bundle"

echo "=== Security Bundle starting ==="
echo "Tenant config: $TENANT_CONFIG"

# ── 1. Read tenant config ───────────────────────────────────────────────────
if [ ! -f "$TENANT_CONFIG" ]; then
    echo "ERROR: Tenant config not found at $TENANT_CONFIG"
    exit 1
fi

# Source tenant config (sets tenant_name, mysql_host, etc.)
set -a
source "$TENANT_CONFIG"
set +a

CONFIG_DIR=$(dirname "$TENANT_CONFIG")
echo "Tenant: $tenant_name"
echo "Config dir: $CONFIG_DIR"

# ── 2. Copy tenant nginx configs ────────────────────────────────────────────
echo "Configuring nginx..."
if [ -d "$CONFIG_DIR/nginx" ]; then
    # Copy main nginx.conf if provided
    [ -f "$CONFIG_DIR/nginx/nginx.conf" ] && cp "$CONFIG_DIR/nginx/nginx.conf" /etc/nginx/nginx.conf

    # Copy proxy_params if provided
    [ -f "$CONFIG_DIR/nginx/proxy_params" ] && cp "$CONFIG_DIR/nginx/proxy_params" /etc/nginx/proxy_params

    # Copy site configs
    if [ -d "$CONFIG_DIR/nginx/sites-enabled" ]; then
        rm -f /etc/nginx/sites-enabled/*
        cp "$CONFIG_DIR/nginx/sites-enabled/"* /etc/nginx/sites-enabled/
    fi

    # Copy tenant-specific snippets (override common)
    if [ -d "$CONFIG_DIR/nginx/snippets" ]; then
        cp "$CONFIG_DIR/nginx/snippets/"* /etc/nginx/snippets/
    fi
fi

# ── 3. Copy common nginx snippets (don't overwrite tenant-specific) ──────────
echo "Copying common nginx snippets..."
cp -n "$COMMON_DIR/wazuh-common/../nginx/snippets/"* /etc/nginx/snippets/ 2>/dev/null || true
# The common snippets are already in /etc/nginx/snippets/ from Dockerfile COPY

# ── 4. Configure ModSecurity ────────────────────────────────────────────────
echo "Configuring ModSecurity..."
# Common modsecurity base is already in /etc/nginx/modsecurity/ from Dockerfile
# Copy tenant-specific exclusions and overrides
if [ -d "$CONFIG_DIR/modsecurity" ]; then
    cp "$CONFIG_DIR/modsecurity/"* /etc/nginx/modsecurity/
fi

# Ensure unicode mapping exists
if [ ! -f /etc/nginx/modsecurity/unicode.mapping ]; then
    cp /etc/nginx/modsecurity/crs/unicode.mapping /etc/nginx/modsecurity/ 2>/dev/null || \
    curl -fsSL -o /etc/nginx/modsecurity/unicode.mapping \
        "https://raw.githubusercontent.com/owasp-modsecurity/ModSecurity/v3/master/unicode.mapping" || true
fi

# ── 5. Configure CrowdSec ───────────────────────────────────────────────────
echo "Configuring CrowdSec..."
if [ -d "$CONFIG_DIR/crowdsec" ]; then
    # Copy acquisition config
    [ -f "$CONFIG_DIR/crowdsec/acquis.yaml" ] && cp "$CONFIG_DIR/crowdsec/acquis.yaml" /etc/crowdsec/acquis.yaml

    # Copy whitelist
    if [ -f "$CONFIG_DIR/crowdsec/whitelist.yaml" ]; then
        mkdir -p /etc/crowdsec/parsers/s02-enrich/
        cp "$CONFIG_DIR/crowdsec/whitelist.yaml" /etc/crowdsec/parsers/s02-enrich/whitelist.yaml
    fi

    # Install collections
    if [ -f "$CONFIG_DIR/crowdsec/collections.txt" ]; then
        while IFS= read -r collection; do
            collection=$(echo "$collection" | sed 's/#.*//' | xargs)
            [ -n "$collection" ] && cscli collections install "$collection" --force 2>/dev/null || true
        done < "$CONFIG_DIR/crowdsec/collections.txt"
    fi
fi

# ── 6. Configure Fail2Ban ───────────────────────────────────────────────────
echo "Configuring Fail2Ban..."
if [ -d "$CONFIG_DIR/fail2ban" ]; then
    [ -f "$CONFIG_DIR/fail2ban/jail.local" ] && cp "$CONFIG_DIR/fail2ban/jail.local" /etc/fail2ban/jail.local

    # Copy custom filters (strip 'filter-' prefix for fail2ban filter.d naming)
    for f in "$CONFIG_DIR/fail2ban/filter-"*.conf; do
        [ -f "$f" ] || continue
        fname=$(basename "$f")
        # filter-nginx-modsecurity.conf -> nginx-modsecurity.conf
        target_name="${fname#filter-}"
        cp "$f" "/etc/fail2ban/filter.d/$target_name"
    done
fi

# ── 7. Configure Wazuh ──────────────────────────────────────────────────────
echo "Configuring Wazuh..."

# Merge base + tenant ossec.conf
WAZUH_BASE="$COMMON_DIR/wazuh-common/ossec-base.conf"
WAZUH_TENANT="$CONFIG_DIR/wazuh/ossec-tenant.conf"
OSSEC_CONF="/var/ossec/etc/ossec.conf"

if [ -f "$WAZUH_BASE" ]; then
    # Start with XML header
    echo '<!-- Security Bundle: auto-generated ossec.conf -->' > "$OSSEC_CONF"
    echo '<ossec_config>' >> "$OSSEC_CONF"

    # Extract inner content from base (strip ossec_config tags)
    sed -n '/<ossec_config>/,/<\/ossec_config>/{/<ossec_config>/d;/<\/ossec_config>/d;p}' \
        "$WAZUH_BASE" >> "$OSSEC_CONF"

    # Append tenant-specific config if exists
    if [ -f "$WAZUH_TENANT" ]; then
        echo '' >> "$OSSEC_CONF"
        echo '  <!-- Tenant-specific configuration -->' >> "$OSSEC_CONF"
        sed -n '/<ossec_config>/,/<\/ossec_config>/{/<ossec_config>/d;/<\/ossec_config>/d;p}' \
            "$WAZUH_TENANT" >> "$OSSEC_CONF"
    fi

    echo '</ossec_config>' >> "$OSSEC_CONF"
fi

# Copy custom decoders
if [ -d "$COMMON_DIR/wazuh-common/decoders" ]; then
    mkdir -p /var/ossec/etc/decoders/
    cp "$COMMON_DIR/wazuh-common/decoders/"*.xml /var/ossec/etc/decoders/
fi

# Copy custom rules
if [ -d "$COMMON_DIR/wazuh-common/rules" ]; then
    mkdir -p /var/ossec/etc/rules/
    cp "$COMMON_DIR/wazuh-common/rules/"*.xml /var/ossec/etc/rules/
fi

# ── 8. Wazuh certs (always regenerate since /etc is ephemeral) ───────────────
echo "Setting up Wazuh certificates..."
WAZUH_DATA="/var/ossec/data"
CERT_DIR="/etc/wazuh-indexer/certs"
DASH_CERT_DIR="/etc/wazuh-dashboard/certs"
mkdir -p "$CERT_DIR" "$DASH_CERT_DIR"

# Generate root CA
openssl genrsa -out "$CERT_DIR/root-ca-key.pem" 2048 2>/dev/null
openssl req -new -x509 -sha256 -key "$CERT_DIR/root-ca-key.pem" \
    -out "$CERT_DIR/root-ca.pem" -days 3650 \
    -subj "/C=US/L=California/O=Wazuh/OU=Wazuh/CN=wazuh-root-ca" 2>/dev/null

# Generate indexer cert signed by root CA (CN=wazuh-indexer)
openssl genrsa -out "$CERT_DIR/indexer-key.pem" 2048 2>/dev/null
openssl req -new -key "$CERT_DIR/indexer-key.pem" -out /tmp/indexer.csr \
    -subj "/C=US/L=California/O=Wazuh/OU=Wazuh/CN=wazuh-indexer" 2>/dev/null
openssl x509 -req -in /tmp/indexer.csr -CA "$CERT_DIR/root-ca.pem" \
    -CAkey "$CERT_DIR/root-ca-key.pem" -CAcreateserial \
    -out "$CERT_DIR/indexer.pem" -days 3650 -sha256 2>/dev/null

# Generate admin cert signed by root CA (CN=admin — MUST differ from node CN)
openssl genrsa -out "$CERT_DIR/admin-key.pem" 2048 2>/dev/null
openssl req -new -key "$CERT_DIR/admin-key.pem" -out /tmp/admin.csr \
    -subj "/C=US/L=California/O=Wazuh/OU=Wazuh/CN=admin" 2>/dev/null
openssl x509 -req -in /tmp/admin.csr -CA "$CERT_DIR/root-ca.pem" \
    -CAkey "$CERT_DIR/root-ca-key.pem" -CAcreateserial \
    -out "$CERT_DIR/admin.pem" -days 3650 -sha256 2>/dev/null

chmod 600 "$CERT_DIR"/*.pem
chown -R wazuh-indexer:wazuh-indexer "$CERT_DIR" 2>/dev/null || true

# Generate dashboard cert signed by same root CA
openssl genrsa -out "$DASH_CERT_DIR/dashboard-key.pem" 2048 2>/dev/null
openssl req -new -key "$DASH_CERT_DIR/dashboard-key.pem" -out /tmp/dashboard.csr \
    -subj "/C=US/L=California/O=Wazuh/OU=Wazuh/CN=wazuh-dashboard" 2>/dev/null
openssl x509 -req -in /tmp/dashboard.csr -CA "$CERT_DIR/root-ca.pem" \
    -CAkey "$CERT_DIR/root-ca-key.pem" -CAcreateserial \
    -out "$DASH_CERT_DIR/dashboard.pem" -days 3650 -sha256 2>/dev/null
cp "$CERT_DIR/root-ca.pem" "$DASH_CERT_DIR/root-ca.pem"
chmod 600 "$DASH_CERT_DIR"/*.pem
chown -R wazuh-dashboard:wazuh-dashboard "$DASH_CERT_DIR" 2>/dev/null || true

rm -f /tmp/indexer.csr /tmp/admin.csr /tmp/dashboard.csr

# Ensure opensearch.yml has our admin cert CN in admin_dn
if ! grep -q 'CN=admin' /etc/wazuh-indexer/opensearch.yml 2>/dev/null; then
    sed -i '/plugins.security.authcz.admin_dn:/a\- "CN=admin,OU=Wazuh,O=Wazuh,L=California,C=US"' \
        /etc/wazuh-indexer/opensearch.yml
fi
# Ensure our node CN is in nodes_dn
if ! grep -q 'CN=wazuh-indexer' /etc/wazuh-indexer/opensearch.yml 2>/dev/null; then
    sed -i '/plugins.security.nodes_dn:/a\- "CN=wazuh-indexer"' \
        /etc/wazuh-indexer/opensearch.yml
fi

touch "$WAZUH_DATA/.initialized"
echo "Wazuh certificates ready."

# ── 9. Set MySQL env vars for log-shipper and watchdog ───────────────────────
echo "Setting MySQL connection..."
export MYSQL_HOST="${mysql_host:-127.0.0.1}"
export MYSQL_PORT="${mysql_port:-3306}"
export MYSQL_USER="${mysql_user:-security_bundle}"
export MYSQL_PASS="${mysql_pass}"
export MYSQL_DB="${mysql_db:-security_monitoring}"
export TENANT_NAME="${tenant_name}"

# Write env file for supervisord child processes
cat > /etc/security-bundle.env <<ENVEOF
MYSQL_HOST=${MYSQL_HOST}
MYSQL_PORT=${MYSQL_PORT}
MYSQL_USER=${MYSQL_USER}
MYSQL_PASS=${MYSQL_PASS}
MYSQL_DB=${MYSQL_DB}
TENANT_NAME=${TENANT_NAME}
OPENSEARCH_JAVA_OPTS=-Xms384m -Xmx384m
HACTL_ENABLED=${HACTL_ENABLED:-false}
HACTL_NODE_ID=${HACTL_NODE_ID:-none}
ENVEOF
chmod 600 /etc/security-bundle.env

# ── 10. Load watchdog services config ────────────────────────────────────────
if [ -f "$CONFIG_DIR/watchdog/services.conf" ]; then
    cp "$CONFIG_DIR/watchdog/services.conf" /opt/security-bundle/scripts/services.conf
fi

# ── 10b. Configure HA Controller ──────────────────────────────────────────────
if [ "${hactl_enabled:-false}" = "true" ]; then
    echo "Configuring HA Controller..."
    export HACTL_ENABLED=true
    export HACTL_NODE_ID="${hactl_node_id}"

    if [ -f "$CONFIG_DIR/ha-controller.yml" ]; then
        cp "$CONFIG_DIR/ha-controller.yml" /config/ha-controller.yml
        echo "HA Controller: enabled, node=$HACTL_NODE_ID"
    else
        echo "WARNING: hactl_enabled=true but no ha-controller.yml found in $CONFIG_DIR"
        export HACTL_ENABLED=false
    fi
else
    echo "HA Controller: disabled"
    export HACTL_ENABLED=false
    export HACTL_NODE_ID=none
fi

# ── 11. vm.max_map_count for OpenSearch ──────────────────────────────────────
CURRENT_MAP_COUNT=$(cat /proc/sys/vm/max_map_count 2>/dev/null || echo 0)
if [ "$CURRENT_MAP_COUNT" -lt 262144 ]; then
    echo "WARNING: vm.max_map_count=$CURRENT_MAP_COUNT (need 262144)"
    echo "Set on host: sysctl -w vm.max_map_count=262144"
    # Try to set it (works if --privileged or appropriate capabilities)
    sysctl -w vm.max_map_count=262144 2>/dev/null || true
fi

# ── 12. Create log directories and ensure log files exist ────────────────────
mkdir -p "$LOG_DIR" /var/log/nginx /var/log/wazuh-indexer
chown -R wazuh-indexer:wazuh-indexer /var/log/wazuh-indexer 2>/dev/null || true

# Fail2Ban needs these log files to exist before starting
touch /var/log/auth.log /var/log/fail2ban.log /var/log/nginx/access.log /var/log/nginx/error.log /var/log/modsec_audit.log /var/log/crowdsec.log /var/log/crowdsec_decisions.log

# ── 13. Disable CrowdSec nginx lua bouncer ───────────────────────────────────
# The lua-based CrowdSec nginx bouncer has dependency issues in containerized nginx.
# We use the iptables firewall bouncer (crowdsec-firewall-bouncer) instead, which
# blocks IPs at the network level before they reach nginx.
if [ -f /etc/nginx/conf.d/crowdsec_nginx.conf ]; then
    echo "Disabling CrowdSec lua bouncer (using iptables firewall bouncer instead)"
    mv /etc/nginx/conf.d/crowdsec_nginx.conf /etc/nginx/conf.d/crowdsec_nginx.conf.disabled
fi

# ── 14. Validate nginx config ───────────────────────────────────────────────
echo "Validating nginx configuration..."
nginx -t 2>&1 || echo "WARNING: nginx config validation failed, will attempt to start anyway"

# ── 15. Fix Wazuh Dashboard port ──────────────────────────────────────────────
# Dashboard default config may set port 443 which conflicts with nginx.
# Force port 5601.
if [ -f /etc/wazuh-dashboard/opensearch_dashboards.yml ]; then
    sed -i 's/^server\.port:.*/server.port: 5601/' /etc/wazuh-dashboard/opensearch_dashboards.yml
fi

echo "=== Security Bundle configured, starting supervisord ==="

# Start supervisord in background, then initialize OpenSearch Security
/usr/bin/supervisord -c /etc/supervisor/supervisord.conf &
SUPERVISORD_PID=$!

# ── 16. Initialize OpenSearch Security after indexer starts ───────────────────
echo "Waiting for OpenSearch to start..."
RETRIES=0
while [ $RETRIES -lt 60 ]; do
    if curl -sk https://localhost:9200/ >/dev/null 2>&1; then
        echo "OpenSearch is up, initializing security..."
        sleep 5
        /usr/share/wazuh-indexer/bin/indexer-security-init.sh 2>&1 || \
            echo "WARNING: Security init failed, dashboard may not work"
        break
    fi
    RETRIES=$((RETRIES + 1))
    sleep 5
done

if [ $RETRIES -ge 60 ]; then
    echo "WARNING: OpenSearch did not start within 5 minutes, skipping security init"
fi

# Wait for supervisord (foreground)
wait $SUPERVISORD_PID
