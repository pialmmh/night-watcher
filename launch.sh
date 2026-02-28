#!/bin/bash
#
# Launch a Night-Watcher LXC container from a config file
#
# Usage: ./launch.sh <config-file>
#
# Example:
#   ./launch.sh tenants/bdcom/launch-bdcom1.conf
#

set -e

CONFIG_FILE="${1:-}"

if [ -z "$CONFIG_FILE" ]; then
    echo "Usage: $0 <config-file>"
    echo ""
    echo "Launch a night-watcher container from a config file."
    echo ""
    echo "Example:"
    echo "  $0 tenants/bdcom/launch-bdcom1.conf"
    exit 1
fi

if [ ! -f "$CONFIG_FILE" ]; then
    echo "ERROR: Config file not found: $CONFIG_FILE"
    exit 1
fi

source "$CONFIG_FILE"

# Validate required fields
for var in CONTAINER_NAME BASE_IMAGE BRIDGE_NAME CONTAINER_IP GATEWAY_IP; do
    if [ -z "${!var}" ]; then
        echo "ERROR: $var is required in config file"
        exit 1
    fi
done

IP_ONLY="${CONTAINER_IP%%/*}"
SUBNET_MASK="${CONTAINER_IP##*/}"

echo "========================================================"
echo "  Night-Watcher Launch"
echo "========================================================"
echo ""
echo "Container:  $CONTAINER_NAME"
echo "Image:      $BASE_IMAGE"
echo "Network:    $CONTAINER_IP on $BRIDGE_NAME (gw: $GATEWAY_IP)"
echo "Resources:  ${MEMORY_LIMIT:-2GB} RAM, ${CPU_LIMIT:-2} CPU"
echo ""

# Check if image exists
if ! lxc image info "$BASE_IMAGE" &>/dev/null; then
    echo "ERROR: Image '$BASE_IMAGE' not found."
    echo ""
    echo "Import the image first:"
    echo "  lxc image import <tarball>.tar.gz --alias $BASE_IMAGE"
    exit 1
fi

# Check if container already exists
if lxc info "$CONTAINER_NAME" &>/dev/null; then
    echo "Container '$CONTAINER_NAME' already exists."
    echo "Stop and delete it first:"
    echo "  lxc stop $CONTAINER_NAME && lxc delete $CONTAINER_NAME"
    exit 1
fi

# Launch
echo "Launching container..."
lxc launch "$BASE_IMAGE" "$CONTAINER_NAME"

# Configure static IP on the network device
# LXD 5.x: profile uses network=<bridge>, so we set ipv4.address directly
lxc config device override "$CONTAINER_NAME" eth0 ipv4.address="$IP_ONLY"

# Set DNS and gateway inside container
lxc exec "$CONTAINER_NAME" -- bash -c "cat > /etc/systemd/network/10-eth0.network << NETEOF
[Match]
Name=eth0

[Network]
Address=$CONTAINER_IP
Gateway=$GATEWAY_IP
DNS=${DNS_SERVERS:-8.8.8.8 8.8.4.4}
NETEOF
systemctl restart systemd-networkd 2>/dev/null || true"

# Resource limits
lxc config set "$CONTAINER_NAME" limits.memory "${MEMORY_LIMIT:-2GB}"
lxc config set "$CONTAINER_NAME" limits.cpu "${CPU_LIMIT:-2}"
lxc config set "$CONTAINER_NAME" security.nesting true

# Set environment variables
lxc config set "$CONTAINER_NAME" environment.HACTL_ENABLED="${HACTL_ENABLED:-false}"
lxc config set "$CONTAINER_NAME" environment.HACTL_NODE_ID="${HACTL_NODE_ID:-none}"
lxc config set "$CONTAINER_NAME" environment.CONSUL_ADDRESS="${CONSUL_ADDRESS:-127.0.0.1:8500}"
lxc config set "$CONTAINER_NAME" environment.MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
lxc config set "$CONTAINER_NAME" environment.MYSQL_PORT="${MYSQL_PORT:-3306}"
lxc config set "$CONTAINER_NAME" environment.MYSQL_USER="${MYSQL_USER:-security_bundle}"
lxc config set "$CONTAINER_NAME" environment.MYSQL_PASS="${MYSQL_PASS:-changeme}"
lxc config set "$CONTAINER_NAME" environment.MYSQL_DB="${MYSQL_DB:-security_monitoring}"

# Create config directory and tenant.conf
lxc exec "$CONTAINER_NAME" -- mkdir -p /config
lxc exec "$CONTAINER_NAME" -- bash -c "cat > /config/tenant.conf << TEOF
tenant_name=\"${TENANT_NAME:-$TENANT_ID}\"
tenant_id=\"${TENANT_ID:-default}\"
mysql_host=\"${MYSQL_HOST:-127.0.0.1}\"
mysql_port=\"${MYSQL_PORT:-3306}\"
mysql_user=\"${MYSQL_USER:-security_bundle}\"
mysql_pass=\"${MYSQL_PASS:-changeme}\"
mysql_db=\"${MYSQL_DB:-security_monitoring}\"
hactl_enabled=\"${HACTL_ENABLED:-false}\"
hactl_node_id=\"${HACTL_NODE_ID:-none}\"
TEOF"

# Restart to apply environment
lxc restart "$CONTAINER_NAME"
sleep 5

# Run entrypoint
lxc exec "$CONTAINER_NAME" -- /entrypoint.sh 2>/dev/null || true
sleep 3

echo ""
echo "========================================================"
echo "  Container Launched"
echo "========================================================"
echo ""
echo "Name:     $CONTAINER_NAME"
echo "IP:       $CONTAINER_IP"
echo "Bridge:   $BRIDGE_NAME"
echo ""
echo "Commands:"
echo "  lxc exec $CONTAINER_NAME -- bash"
echo "  lxc exec $CONTAINER_NAME -- supervisorctl status"
echo "  lxc stop $CONTAINER_NAME"
echo ""
