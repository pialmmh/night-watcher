#!/bin/bash
# Install Wazuh agent on a remote server and point it to the security-bundle manager
#
# Usage: ./install-agent.sh <ssh_wrapper> <server_name> <manager_ip> [registration_password]
#
# Example:
#   ./install-agent.sh /path/to/ssh btcl/dell-sms-master 10.10.195.10 changeme

set -e

SSH_WRAPPER="$1"
SERVER="$2"
MANAGER_IP="$3"
REG_PASSWORD="${4:-}"

if [ -z "$SSH_WRAPPER" ] || [ -z "$SERVER" ] || [ -z "$MANAGER_IP" ]; then
    echo "Usage: $0 <ssh_wrapper> <server_name> <manager_ip> [registration_password]"
    echo ""
    echo "  ssh_wrapper:    path to ssh automation wrapper script"
    echo "  server_name:    server identifier (e.g. btcl/dell-sms-master)"
    echo "  manager_ip:     IP address of the Wazuh manager"
    echo "  registration_password: optional agent registration password"
    exit 1
fi

echo "=== Installing Wazuh agent on $SERVER ==="
echo "Manager IP: $MANAGER_IP"

# Check if agent is already installed
AGENT_CHECK=$("$SSH_WRAPPER" "$SERVER" "dpkg -l wazuh-agent 2>/dev/null | grep -c '^ii'" 2>/dev/null || echo "0")

if [ "$AGENT_CHECK" != "0" ]; then
    echo "Wazuh agent already installed on $SERVER"
    echo "Checking status..."
    "$SSH_WRAPPER" "$SERVER" "systemctl status wazuh-agent --no-pager 2>&1 | head -5"
    exit 0
fi

# Add Wazuh repository and install agent
echo "Adding Wazuh repository..."
"$SSH_WRAPPER" "$SERVER" "curl -fsSL https://packages.wazuh.com/key/GPG-KEY-WAZUH | gpg --dearmor -o /usr/share/keyrings/wazuh.gpg 2>/dev/null"
"$SSH_WRAPPER" "$SERVER" "echo 'deb [signed-by=/usr/share/keyrings/wazuh.gpg] https://packages.wazuh.com/4.x/apt/ stable main' > /etc/apt/sources.list.d/wazuh.list"

echo "Installing wazuh-agent..."
"$SSH_WRAPPER" "$SERVER" "WAZUH_MANAGER='$MANAGER_IP' apt-get update && apt-get install -y wazuh-agent 2>&1 | tail -3"

# Configure agent
echo "Configuring agent to connect to $MANAGER_IP..."
"$SSH_WRAPPER" "$SERVER" "sed -i 's|<address>.*</address>|<address>$MANAGER_IP</address>|' /var/ossec/etc/ossec.conf"

# Set registration password if provided
if [ -n "$REG_PASSWORD" ]; then
    "$SSH_WRAPPER" "$SERVER" "echo '$REG_PASSWORD' > /var/ossec/etc/authd.pass && chmod 600 /var/ossec/etc/authd.pass"
fi

# Enable and start agent
echo "Starting Wazuh agent..."
"$SSH_WRAPPER" "$SERVER" "systemctl daemon-reload && systemctl enable wazuh-agent && systemctl start wazuh-agent"

# Verify
echo "Verifying agent status..."
"$SSH_WRAPPER" "$SERVER" "systemctl status wazuh-agent --no-pager 2>&1 | head -10"

echo ""
echo "=== Wazuh agent installed on $SERVER ==="
echo "Agent should appear in Wazuh dashboard within 1-2 minutes."
