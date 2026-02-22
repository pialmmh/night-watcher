#!/bin/bash
# Wrapper to run wazuh-manager in supervisord
# wazuh-control doesn't have a foreground mode, so we start it
# and then monitor the main analysisd process

/var/ossec/bin/wazuh-control start
sleep 5

# Find the main analysisd PID file
while true; do
    PID_FILE=$(ls /var/ossec/var/run/wazuh-analysisd-*.pid 2>/dev/null | head -1)
    if [ -n "$PID_FILE" ] && [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            sleep 10
            continue
        fi
    fi
    # Process died or PID file missing
    echo "wazuh-analysisd process exited, stopping wrapper"
    /var/ossec/bin/wazuh-control stop 2>/dev/null || true
    exit 1
done
