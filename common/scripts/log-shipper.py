#!/usr/bin/env python3
"""
Log Shipper: tails Wazuh alerts, Fail2Ban bans, and CrowdSec decisions,
then inserts structured records into MySQL.

Reads MySQL connection from environment variables:
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASS, MYSQL_DB, TENANT_NAME
"""

import json
import os
import re
import signal
import sys
import time
from datetime import datetime
from pathlib import Path

import mysql.connector
from mysql.connector import Error as MySQLError

# ── Configuration ────────────────────────────────────────────────────────────

WAZUH_ALERTS_LOG = "/var/ossec/logs/alerts/alerts.json"
FAIL2BAN_LOG = "/var/log/fail2ban.log"
CROWDSEC_DECISIONS_LOG = "/var/log/crowdsec_decisions.log"
SHIPPER_LOG = "/var/log/security-bundle/log-shipper.log"

TAIL_INTERVAL = 2  # seconds between tail checks
BATCH_SIZE = 50     # insert batch size
RECONNECT_DELAY = 30

# ── Globals ──────────────────────────────────────────────────────────────────

running = True
db_conn = None
tenant_name = os.environ.get("TENANT_NAME", "unknown")


def log(level, msg):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"{ts} [{level}] {msg}\n"
    sys.stdout.write(line)
    sys.stdout.flush()
    try:
        with open(SHIPPER_LOG, "a") as f:
            f.write(line)
    except Exception:
        pass


def signal_handler(sig, frame):
    global running
    log("INFO", f"Received signal {sig}, shutting down")
    running = False


signal.signal(signal.SIGTERM, signal_handler)
signal.signal(signal.SIGINT, signal_handler)


# ── MySQL connection ─────────────────────────────────────────────────────────

def get_db_connection():
    global db_conn
    try:
        if db_conn and db_conn.is_connected():
            return db_conn
    except Exception:
        db_conn = None

    host = os.environ.get("MYSQL_HOST", "127.0.0.1")
    port = int(os.environ.get("MYSQL_PORT", "3306"))
    user = os.environ.get("MYSQL_USER", "security_bundle")
    password = os.environ.get("MYSQL_PASS", "")
    database = os.environ.get("MYSQL_DB", "security_monitoring")

    if not password:
        log("WARN", "MYSQL_PASS not set, log shipping to MySQL disabled")
        return None

    try:
        db_conn = mysql.connector.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            database=database,
            connect_timeout=10,
            autocommit=True,
        )
        log("INFO", f"Connected to MySQL at {host}:{port}/{database}")
        return db_conn
    except MySQLError as e:
        log("ERROR", f"MySQL connection failed: {e}")
        return None


def insert_batch(table, columns, rows):
    """Insert a batch of rows into the given table."""
    if not rows:
        return

    conn = get_db_connection()
    if not conn:
        return

    placeholders = ", ".join(["%s"] * len(columns))
    col_names = ", ".join(columns)
    query = f"INSERT INTO {table} ({col_names}) VALUES ({placeholders})"

    try:
        cursor = conn.cursor()
        cursor.executemany(query, rows)
        cursor.close()
    except MySQLError as e:
        log("ERROR", f"Insert into {table} failed: {e}")
        global db_conn
        db_conn = None


# ── File tailers ─────────────────────────────────────────────────────────────

class FileTailer:
    """Tails a file from end, yielding new lines as they appear."""

    def __init__(self, filepath):
        self.filepath = filepath
        self.fh = None
        self.inode = None
        self._open()

    def _open(self):
        try:
            if os.path.exists(self.filepath):
                self.fh = open(self.filepath, "r")
                self.fh.seek(0, 2)  # seek to end
                self.inode = os.stat(self.filepath).st_ino
        except Exception as e:
            log("WARN", f"Cannot open {self.filepath}: {e}")
            self.fh = None

    def read_new_lines(self):
        lines = []
        if not self.fh:
            self._open()
            if not self.fh:
                return lines

        # Check for file rotation (inode change)
        try:
            current_inode = os.stat(self.filepath).st_ino
            if current_inode != self.inode:
                log("INFO", f"File rotated: {self.filepath}")
                self.fh.close()
                self._open()
                if not self.fh:
                    return lines
        except FileNotFoundError:
            return lines

        for line in self.fh:
            line = line.strip()
            if line:
                lines.append(line)

        return lines


# ── Parsers ──────────────────────────────────────────────────────────────────

def parse_wazuh_alert(line):
    """Parse a Wazuh alerts.json line into a row for wazuh_alerts table."""
    try:
        alert = json.loads(line)
        return (
            tenant_name,
            alert.get("timestamp", datetime.now().isoformat()),
            alert.get("rule", {}).get("id", ""),
            alert.get("rule", {}).get("level", 0),
            alert.get("rule", {}).get("description", ""),
            alert.get("agent", {}).get("name", "local"),
            alert.get("data", {}).get("srcip", alert.get("srcip", "")),
            json.dumps(alert.get("rule", {}).get("groups", [])),
            line[:4000],  # raw JSON truncated
        )
    except (json.JSONDecodeError, KeyError) as e:
        log("WARN", f"Failed to parse Wazuh alert: {e}")
        return None


FAIL2BAN_BAN_RE = re.compile(
    r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}),\d+\s+fail2ban\.actions\s+\[\d+\]:\s+(\w+)\s+\[(\S+)\]\s+(Ban|Unban)\s+(\S+)"
)


def parse_fail2ban_line(line):
    """Parse a Fail2Ban log line for Ban/Unban events."""
    m = FAIL2BAN_BAN_RE.search(line)
    if not m:
        return None
    ts, level, jail, action, ip = m.groups()
    return (
        tenant_name,
        ts,
        "fail2ban",
        jail,
        action.lower(),
        ip,
        "",  # reason
        0,   # duration_sec
    )


def parse_crowdsec_decision(line):
    """Parse a CrowdSec decision log line."""
    try:
        data = json.loads(line)
        return (
            tenant_name,
            data.get("timestamp", datetime.now().isoformat()),
            "crowdsec",
            data.get("scenario", ""),
            data.get("type", "ban"),
            data.get("value", ""),
            data.get("reason", ""),
            int(data.get("duration", "0").rstrip("hms") or 0),
        )
    except (json.JSONDecodeError, KeyError, ValueError):
        # Fallback: try text format
        return None


# ── Main loop ────────────────────────────────────────────────────────────────

def main():
    log("INFO", f"Log shipper starting for tenant: {tenant_name}")

    # Wait for log files to appear (services may still be starting)
    log("INFO", "Waiting for log files...")
    time.sleep(10)

    tailers = {
        "wazuh": FileTailer(WAZUH_ALERTS_LOG),
        "fail2ban": FileTailer(FAIL2BAN_LOG),
        "crowdsec": FileTailer(CROWDSEC_DECISIONS_LOG),
    }

    wazuh_batch = []
    ban_batch = []

    while running:
        # ── Wazuh alerts ──
        for line in tailers["wazuh"].read_new_lines():
            row = parse_wazuh_alert(line)
            if row:
                wazuh_batch.append(row)

        if len(wazuh_batch) >= BATCH_SIZE:
            insert_batch(
                "wazuh_alerts",
                ["tenant", "alert_timestamp", "rule_id", "rule_level",
                 "rule_description", "agent_name", "src_ip", "rule_groups", "raw_json"],
                wazuh_batch,
            )
            wazuh_batch = []

        # ── Fail2Ban events ──
        for line in tailers["fail2ban"].read_new_lines():
            row = parse_fail2ban_line(line)
            if row:
                ban_batch.append(row)

        # ── CrowdSec decisions ──
        for line in tailers["crowdsec"].read_new_lines():
            row = parse_crowdsec_decision(line)
            if row:
                ban_batch.append(row)

        if len(ban_batch) >= BATCH_SIZE:
            insert_batch(
                "ban_events",
                ["tenant", "event_timestamp", "source", "jail_or_scenario",
                 "action", "ip", "reason", "duration_sec"],
                ban_batch,
            )
            ban_batch = []

        # Flush partial batches every cycle
        if wazuh_batch:
            insert_batch(
                "wazuh_alerts",
                ["tenant", "alert_timestamp", "rule_id", "rule_level",
                 "rule_description", "agent_name", "src_ip", "rule_groups", "raw_json"],
                wazuh_batch,
            )
            wazuh_batch = []

        if ban_batch:
            insert_batch(
                "ban_events",
                ["tenant", "event_timestamp", "source", "jail_or_scenario",
                 "action", "ip", "reason", "duration_sec"],
                ban_batch,
            )
            ban_batch = []

        time.sleep(TAIL_INTERVAL)

    log("INFO", "Log shipper stopped")


if __name__ == "__main__":
    # Retry loop for MySQL connection
    while running:
        try:
            main()
        except Exception as e:
            log("ERROR", f"Log shipper crashed: {e}")
            log("INFO", f"Restarting in {RECONNECT_DELAY}s...")
            time.sleep(RECONNECT_DELAY)
