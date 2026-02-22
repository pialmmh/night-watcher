#!/bin/bash
set -e

# Security Bundle - deploy to tenant server
# Usage: ./deploy.sh <tenant>
# Example: ./deploy.sh btclsms

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SSH_BASE="/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers"
IMAGE="telcobright/security-bundle:latest"
REMOTE_CONFIG_DIR="/opt/security-bundle/config"
REMOTE_LOG_DIR="/var/log/security-bundle"

TENANT="$1"

if [ -z "$TENANT" ]; then
    echo "Usage: $0 <tenant>"
    echo ""
    echo "Available tenants:"
    ls -1 "$SCRIPT_DIR/tenants/" 2>/dev/null
    exit 1
fi

TENANT_DIR="$SCRIPT_DIR/tenants/$TENANT"
TENANT_CONF="$TENANT_DIR/tenant.conf"

if [ ! -f "$TENANT_CONF" ]; then
    echo "ERROR: Tenant config not found: $TENANT_CONF"
    exit 1
fi

# ── Load tenant config ──────────────────────────────────────────────────────
source "$TENANT_CONF"
SSH="$SSH_BASE/$ssh_tenant/ssh"

if [ ! -f "$SSH" ]; then
    echo "ERROR: SSH wrapper not found: $SSH"
    exit 1
fi

echo "=== Deploying Security Bundle ==="
echo "Tenant:   $tenant_name"
echo "Server:   $ssh_tenant / $ssh_server"
echo "SSH:      $SSH"
echo "Image:    $IMAGE"
echo ""

# ── 1. Pre-flight checks ────────────────────────────────────────────────────
echo "[1/7] Pre-flight checks..."

echo "  Checking SSH access..."
"$SSH" "$ssh_server" "whoami" || { echo "ERROR: Cannot SSH to $ssh_server"; exit 1; }

echo "  Checking Docker..."
"$SSH" "$ssh_server" "docker --version 2>&1 | head -1" || { echo "ERROR: Docker not found on $ssh_server"; exit 1; }

echo "  Checking vm.max_map_count..."
MAP_COUNT=$("$SSH" "$ssh_server" "cat /proc/sys/vm/max_map_count")
if [ "$MAP_COUNT" -lt 262144 ]; then
    echo "  Setting vm.max_map_count=262144..."
    "$SSH" "$ssh_server" "sudo sysctl -w vm.max_map_count=262144"
    "$SSH" "$ssh_server" "echo 'vm.max_map_count=262144' | sudo tee -a /etc/sysctl.conf"
fi

# ── 2. Create MySQL tables ──────────────────────────────────────────────────
echo "[2/7] Creating MySQL tables (if not exist)..."
if [ -n "$mysql_host" ] && [ -n "$mysql_pass" ]; then
    mysql -h "$mysql_host" -P "${mysql_port:-3306}" -u "$mysql_user" -p"$mysql_pass" "$mysql_db" \
        < "$SCRIPT_DIR/common/mysql/schema.sql" 2>/dev/null || \
        echo "  WARNING: Could not create tables (may already exist or MySQL unreachable)"
else
    echo "  Skipping MySQL setup (no credentials in tenant.conf)"
fi

# ── 3. Copy config to server ────────────────────────────────────────────────
echo "[3/7] Copying tenant config to $ssh_server:$REMOTE_CONFIG_DIR..."

# Create remote directories
"$SSH" "$ssh_server" "sudo mkdir -p $REMOTE_CONFIG_DIR $REMOTE_LOG_DIR /var/log/nginx"

# Create a tar of the tenant config
TMPTAR=$(mktemp /tmp/security-bundle-config-XXXXXX.tar.gz)
tar czf "$TMPTAR" -C "$TENANT_DIR" .

# Copy to server
scp_target="$deploy_user@$ssh_server:$TMPTAR" 2>/dev/null || true
"$SSH" "$ssh_server" "sudo mkdir -p $REMOTE_CONFIG_DIR"

# Use SSH wrapper to copy - create temp file, then move
cat "$TMPTAR" | "$SSH" "$ssh_server" "cat > /tmp/security-bundle-config.tar.gz"
"$SSH" "$ssh_server" "sudo tar xzf /tmp/security-bundle-config.tar.gz -C $REMOTE_CONFIG_DIR && rm /tmp/security-bundle-config.tar.gz"
rm -f "$TMPTAR"

echo "  Config deployed to $REMOTE_CONFIG_DIR"

# ── 4. Load Docker image ────────────────────────────────────────────────────
echo "[4/7] Loading Docker image..."

# Check if image exists locally
if docker image inspect "$IMAGE" &>/dev/null; then
    echo "  Saving image to tar..."
    IMGTMP=$(mktemp /tmp/security-bundle-image-XXXXXX.tar.gz)
    docker save "$IMAGE" | gzip > "$IMGTMP"

    echo "  Transferring image to $ssh_server (this may take a while)..."
    cat "$IMGTMP" | "$SSH" "$ssh_server" "cat > /tmp/security-bundle-image.tar.gz"
    "$SSH" "$ssh_server" "docker load < /tmp/security-bundle-image.tar.gz && rm /tmp/security-bundle-image.tar.gz"
    rm -f "$IMGTMP"
else
    echo "  Image not found locally, attempting docker pull on remote..."
    "$SSH" "$ssh_server" "docker pull $IMAGE" || { echo "ERROR: Cannot get image"; exit 1; }
fi

# ── 5. Stop existing container ───────────────────────────────────────────────
echo "[5/7] Stopping existing container (if any)..."
"$SSH" "$ssh_server" "docker stop security-bundle 2>/dev/null; docker rm security-bundle 2>/dev/null" || true

# ── 6. Run container ────────────────────────────────────────────────────────
echo "[6/7] Starting security-bundle container..."

PORTS=""
IFS=',' read -ra PORT_ARRAY <<< "${container_ports:-80,443,1514,1515,5601}"
for port in "${PORT_ARRAY[@]}"; do
    # With host networking, ports are exposed directly
    true
done

"$SSH" "$ssh_server" "docker run -d \
    --name security-bundle \
    --network host \
    --memory ${container_memory:-2g} \
    --restart unless-stopped \
    -v $REMOTE_CONFIG_DIR:/config:ro \
    -v /var/log/nginx:/var/log/nginx \
    -v $REMOTE_LOG_DIR:/var/log/security-bundle \
    -v /etc/nginx/ssl:/etc/nginx/ssl:ro \
    -v security-bundle-wazuh:/var/ossec/data \
    -v security-bundle-indexer:/var/lib/wazuh-indexer \
    -e TENANT_CONFIG=/config/tenant.conf \
    $IMAGE"

echo "  Container started."

# ── 7. Verify ────────────────────────────────────────────────────────────────
echo "[7/7] Verifying deployment..."
sleep 5

echo ""
echo "  Container status:"
"$SSH" "$ssh_server" "docker ps --filter name=security-bundle --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'"

echo ""
echo "  Process status:"
"$SSH" "$ssh_server" "docker exec security-bundle supervisorctl status 2>&1" || echo "  (supervisord may still be starting)"

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Verification commands:"
echo "  # Check all processes"
echo "  $SSH $ssh_server \"docker exec security-bundle supervisorctl status\""
echo ""
echo "  # Check nginx"
echo "  $SSH $ssh_server \"curl -sk https://localhost/ -o /dev/null -w '%{http_code}'\""
echo ""
echo "  # Check Wazuh dashboard"
echo "  $SSH $ssh_server \"curl -sk https://localhost:5601/ -o /dev/null -w '%{http_code}'\""
echo ""
echo "  # View logs"
echo "  $SSH $ssh_server \"docker logs security-bundle --tail 50\""
