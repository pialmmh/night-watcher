#!/bin/bash
#
# Deploy Night-Watcher HA Cluster to BDCOM (3 nodes)
#
# Usage: ./deploy-bdcom.sh [step]
#
# Steps (run all if no argument):
#   image     - SCP image + import on all 3 servers
#   launch    - Launch containers on all 3 servers
#   consul    - Configure Consul cluster
#   configs   - Push tenant configs (ha-controller.yml, nginx, nodes.json)
#   mysql     - Create security_monitoring database
#   verify    - Verify cluster health
#   all       - Run all steps in order
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TENANT_DIR="$SCRIPT_DIR/tenants/bdcom"

# LXC image
IMAGE_PATH="/home/mustafa/telcobright-projects/vm-images/images/lxc/night-watcher-v1-latest.tar.gz"
IMAGE_ALIAS="night-watcher-base-v.1.0.0"

# SSH wrapper (uses the standard ssh-automation inventory)
SSH_WRAPPER="/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/bdcom/ssh"
SSH_KEYS_DIR="/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/bdcom/keys"
SSH_HOSTS_DIR="/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/bdcom/hosts"

# Node definitions: container_ip node_id
declare -A CONTAINER_IPS
CONTAINER_IPS[bdcom1]="10.10.199.200"
CONTAINER_IPS[bdcom2]="10.10.198.200"
CONTAINER_IPS[bdcom3]="10.10.197.200"

declare -A NODE_IDS
NODE_IDS[bdcom1]="bdcom-nw-1"
NODE_IDS[bdcom2]="bdcom-nw-2"
NODE_IDS[bdcom3]="bdcom-nw-3"

NODE_ORDER=(bdcom1 bdcom2 bdcom3)

# SSH via wrapper
ssh_cmd() {
    local node="$1"; shift
    "$SSH_WRAPPER" "$node" "$@"
}

# SCP using credentials from host file
scp_cmd() {
    local src="$1" node="$2" dest="$3"
    local host_file="$SSH_HOSTS_DIR/$node"
    local host=$(grep -E "^host=" "$host_file" | cut -d= -f2)
    local port=$(grep -E "^port=" "$host_file" | cut -d= -f2)
    local user=$(grep -E "^user=" "$host_file" | cut -d= -f2)
    local key=$(grep -E "^key=" "$host_file" | cut -d= -f2)
    scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR \
        -i "$SSH_KEYS_DIR/$key" -P "$port" "$src" "${user}@${host}:${dest}"
}

get_container_ip() { echo "${CONTAINER_IPS[$1]}"; }
get_node_id()      { echo "${NODE_IDS[$1]}"; }

# ── Step: SCP image + import ────────────────────────────────────────────────

step_image() {
    echo "=== Step: SCP LXC image to all 3 servers ==="
    echo ""

    if [ ! -f "$IMAGE_PATH" ]; then
        echo "ERROR: Image not found: $IMAGE_PATH"
        exit 1
    fi

    for node in "${NODE_ORDER[@]}"; do
        echo "[$node] Copying image (2.1 GB — this will take a while)..."
        scp_cmd "$IMAGE_PATH" "$node" "/tmp/night-watcher.tar.gz"
        echo "[$node] Image copied."
    done

    echo ""
    echo "=== Step: Import LXC image on all 3 servers ==="
    echo ""

    for node in "${NODE_ORDER[@]}"; do
        echo "[$node] Importing image..."

        # Check if image already exists
        if ssh_cmd "$node" "sudo lxc image info $IMAGE_ALIAS" &>/dev/null; then
            echo "[$node] Image '$IMAGE_ALIAS' already exists, deleting old one..."
            ssh_cmd "$node" "sudo lxc image delete $IMAGE_ALIAS"
        fi

        ssh_cmd "$node" "sudo lxc image import /tmp/night-watcher.tar.gz --alias $IMAGE_ALIAS"
        echo "[$node] Image imported."
    done

    echo ""
    echo "Image deployment complete."
}

# ── Step: Launch containers ──────────────────────────────────────────────────

step_launch() {
    echo "=== Step: Launch containers ==="
    echo ""

    for node in "${NODE_ORDER[@]}"; do
        local container_ip=$(get_container_ip $node)
        local container_name="night-watcher-${node}"

        echo "[$node] Launching $container_name (IP: $container_ip)..."

        # Copy launch config to server
        scp_cmd "$TENANT_DIR/launch-${node}.conf" "$node" "/tmp/launch-nw.conf"

        # Copy launch script to server
        scp_cmd "$SCRIPT_DIR/launch.sh" "$node" "/tmp/launch-nw.sh"
        ssh_cmd "$node" "chmod +x /tmp/launch-nw.sh"

        # Run launch script
        ssh_cmd "$node" "sudo /tmp/launch-nw.sh /tmp/launch-nw.conf" || {
            echo "[$node] WARNING: Launch may have failed. Check manually."
        }

        echo "[$node] Container launched."
        echo ""
    done
}

# ── Step: Configure Consul ───────────────────────────────────────────────────

step_consul() {
    echo "=== Step: Configure Consul cluster ==="
    echo ""

    ALL_IPS=""
    for node in "${NODE_ORDER[@]}"; do
        local cip=$(get_container_ip $node)
        if [ -n "$ALL_IPS" ]; then ALL_IPS="$ALL_IPS, "; fi
        ALL_IPS="$ALL_IPS\"$cip\""
    done

    for node in "${NODE_ORDER[@]}"; do
        local container_ip=$(get_container_ip $node)
        local node_id=$(get_node_id $node)
        local container_name="night-watcher-${node}"

        echo "[$node] Configuring Consul on $container_name..."

        ssh_cmd "$node" "sudo lxc exec $container_name -- bash -c 'mkdir -p /etc/consul.d && cat > /etc/consul.d/consul.hcl << CEOF
datacenter = \"dc1\"
node_name = \"$node_id\"
server = true
bootstrap_expect = 3
bind_addr = \"$container_ip\"
client_addr = \"0.0.0.0\"
retry_join = [$ALL_IPS]
ui_config {
  enabled = true
}
data_dir = \"/opt/consul\"
CEOF'"

        echo "[$node] Restarting Consul..."
        ssh_cmd "$node" "sudo lxc exec $container_name -- bash -c 'systemctl restart consul 2>/dev/null || consul agent -config-dir=/etc/consul.d -data-dir=/opt/consul &'" || true

        echo "[$node] Consul configured."
        echo ""
    done

    echo "Waiting 15s for Consul cluster to elect leader..."
    sleep 15

    # Verify
    echo "Consul members:"
    ssh_cmd "bdcom1" "sudo lxc exec night-watcher-bdcom1 -- consul members" || echo "WARNING: consul members check failed"
    echo ""
}

# ── Step: Push tenant configs ────────────────────────────────────────────────

step_configs() {
    echo "=== Step: Push tenant configs into containers ==="
    echo ""

    for node in "${NODE_ORDER[@]}"; do
        local container_name="night-watcher-${node}"

        echo "[$node] Pushing configs..."
        # Create a temp tar of tenant configs
        local tmptar=$(mktemp /tmp/nw-bdcom-config-XXXXXX.tar.gz)
        tar czf "$tmptar" -C "$TENANT_DIR" \
            ha-controller.yml \
            nodes.json \
            nginx/sites-enabled/security-dashboard \
            nginx/nginx.conf \
            nginx/proxy_params

        scp_cmd "$tmptar" "$node" "/tmp/nw-config.tar.gz"
        rm -f "$tmptar"

        # Extract inside container
        ssh_cmd "$node" "sudo lxc exec $container_name -- mkdir -p /config /tmp/nw-config"
        ssh_cmd "$node" "sudo lxc file push /tmp/nw-config.tar.gz $container_name/tmp/nw-config.tar.gz"
        ssh_cmd "$node" "sudo lxc exec $container_name -- bash -c 'cd /tmp && tar xzf /tmp/nw-config.tar.gz -C /tmp/nw-config'"

        # Copy files to correct locations
        ssh_cmd "$node" "sudo lxc exec $container_name -- cp /tmp/nw-config/ha-controller.yml /config/ha-controller.yml"
        ssh_cmd "$node" "sudo lxc exec $container_name -- cp /tmp/nw-config/nodes.json /var/www/security-dashboard/nodes.json"
        ssh_cmd "$node" "sudo lxc exec $container_name -- cp /tmp/nw-config/nginx/sites-enabled/security-dashboard /etc/nginx/sites-enabled/security-dashboard"
        ssh_cmd "$node" "sudo lxc exec $container_name -- cp /tmp/nw-config/nginx/nginx.conf /etc/nginx/nginx.conf"
        ssh_cmd "$node" "sudo lxc exec $container_name -- cp /tmp/nw-config/nginx/proxy_params /etc/nginx/proxy_params"

        # Reload nginx
        ssh_cmd "$node" "sudo lxc exec $container_name -- nginx -t && sudo lxc exec $container_name -- nginx -s reload" || echo "[$node] WARNING: nginx reload failed"

        # Restart hactl
        ssh_cmd "$node" "sudo lxc exec $container_name -- supervisorctl restart hactl" || echo "[$node] WARNING: hactl restart failed"

        # Cleanup
        ssh_cmd "$node" "sudo lxc exec $container_name -- rm -rf /tmp/nw-config /tmp/nw-config.tar.gz"

        echo "[$node] Configs pushed."
        echo ""
    done
}

# ── Step: Create MySQL database ──────────────────────────────────────────────

step_mysql() {
    echo "=== Step: Create security_monitoring database ==="
    echo ""

    echo "Running schema.sql against MySQL at 10.10.199.10 via bdcom1..."

    # Copy schema to first server, then run via container or directly
    scp_cmd "$SCRIPT_DIR/common/mysql/schema.sql" "bdcom1" "/tmp/nw-schema.sql"

    # Try running mysql from the first container
    ssh_cmd "bdcom1" "sudo lxc exec night-watcher-bdcom1 -- bash -c 'mysql -h 10.10.199.10 -P 3306 -u security_bundle -pchangeme security_monitoring < /tmp/nw-schema.sql'" 2>/dev/null || {
        echo "WARNING: MySQL schema creation failed."
        echo "You may need to:"
        echo "  1. Create the database: CREATE DATABASE security_monitoring;"
        echo "  2. Create the user: CREATE USER 'security_bundle'@'%' IDENTIFIED BY 'changeme';"
        echo "  3. Grant permissions: GRANT ALL ON security_monitoring.* TO 'security_bundle'@'%';"
        echo "  4. Run schema manually: mysql -h 10.10.199.10 -u security_bundle -p security_monitoring < common/mysql/schema.sql"
    }

    echo ""
}

# ── Step: Verify ─────────────────────────────────────────────────────────────

step_verify() {
    echo "=== Step: Verify cluster ==="
    echo ""

    echo "1. Consul members:"
    ssh_cmd "bdcom1" "sudo lxc exec night-watcher-bdcom1 -- consul members" || echo "FAILED"
    echo ""

    echo "2. Supervisord status (all nodes):"
    for node in "${NODE_ORDER[@]}"; do
        local container_name="night-watcher-${node}"
        echo "--- $node ---"
        ssh_cmd "$node" "sudo lxc exec $container_name -- supervisorctl status" || echo "FAILED"
        echo ""
    done

    echo "3. HA Controller status (via container curl):"
    for node in "${NODE_ORDER[@]}"; do
        local container_ip=$(get_container_ip $node)
        local container_name="night-watcher-${node}"
        echo "--- $node ($container_ip) ---"
        ssh_cmd "$node" "sudo lxc exec $container_name -- curl -s http://127.0.0.1:7102/status 2>/dev/null" | head -5 || echo "UNREACHABLE"
        echo ""
    done

    echo "4. Dashboard (port 7100, via container):"
    ssh_cmd "bdcom1" "sudo lxc exec night-watcher-bdcom1 -- curl -s http://127.0.0.1:7100/ -o /dev/null -w 'HTTP %{http_code}'" || echo "UNREACHABLE"
    echo ""
}

# ── Main ─────────────────────────────────────────────────────────────────────

STEP="${1:-all}"

case "$STEP" in
    image)   step_image ;;
    launch)  step_launch ;;
    consul)  step_consul ;;
    configs) step_configs ;;
    mysql)   step_mysql ;;
    verify)  step_verify ;;
    all)
        step_image
        step_launch
        step_consul
        step_mysql
        step_configs
        step_verify
        ;;
    *)
        echo "Unknown step: $STEP"
        echo "Valid steps: image, launch, consul, mysql, configs, verify, all"
        exit 1
        ;;
esac

echo ""
echo "=== Done ==="
