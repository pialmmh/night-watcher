#!/usr/bin/env python3
"""Tiny HTTP API returning live status of all security modules + HA controller."""
import http.server, json, subprocess, re, urllib.request

def run(cmd):
    try:
        r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=5)
        return r.stdout.strip()
    except:
        return ""

def get_supervisor_status():
    out = run("supervisorctl status")
    procs = {}
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 2:
            procs[parts[0]] = {"status": parts[1], "detail": " ".join(parts[2:])}
    return procs

def get_fail2ban_status():
    jails = []
    out = run("fail2ban-client status")
    jail_match = re.search(r"Jail list:\s*(.*)", out)
    if jail_match:
        names = [j.strip() for j in jail_match.group(1).split(",") if j.strip()]
        for name in names:
            jout = run("fail2ban-client status " + name)
            jail = {"name": name, "failed": 0, "banned": 0, "total_banned": 0, "banned_ips": []}
            for line in jout.splitlines():
                if "Currently failed" in line:
                    m = re.search(r"(\d+)", line.split(":")[-1])
                    if m: jail["failed"] = int(m.group(1))
                if "Currently banned" in line:
                    m = re.search(r"(\d+)", line.split(":")[-1])
                    if m: jail["banned"] = int(m.group(1))
                if "Total banned" in line:
                    m = re.search(r"(\d+)", line.split(":")[-1])
                    if m: jail["total_banned"] = int(m.group(1))
                if "Banned IP list" in line:
                    ips = line.split(":")[-1].strip()
                    if ips: jail["banned_ips"] = ips.split()
            jails.append(jail)
    return {"jails": jails, "total_jails": len(jails)}

def get_crowdsec_status():
    dec_out = run("cscli decisions list -o json 2>/dev/null") or "null"
    alerts_out = run("cscli alerts list --limit 20 -o json 2>/dev/null") or "null"
    try:
        dec = json.loads(dec_out)
    except:
        dec = None
    try:
        alerts = json.loads(alerts_out)
    except:
        alerts = None
    bouncers = run("cscli bouncers list -o json 2>/dev/null") or "[]"
    try:
        bouncers_list = json.loads(bouncers)
    except:
        bouncers_list = []
    collections = run("cscli collections list -o json 2>/dev/null") or "[]"
    try:
        coll_list = json.loads(collections)
    except:
        coll_list = []
    return {
        "decisions": dec if dec else [],
        "alerts": alerts if alerts else [],
        "decision_count": len(dec) if dec else 0,
        "alert_count": len(alerts) if alerts else 0,
        "bouncers": bouncers_list,
        "collections": coll_list,
    }

def get_nginx_status():
    access_lines = run("wc -l < /var/log/nginx/access.log 2>/dev/null") or "0"
    error_lines = run("wc -l < /var/log/nginx/error.log 2>/dev/null") or "0"
    modsec_lines = run("wc -l < /var/log/modsec_audit.log 2>/dev/null") or "0"
    return {
        "access_log_lines": int(access_lines),
        "error_log_lines": int(error_lines),
        "modsec_log_lines": int(modsec_lines),
    }

def get_wazuh_status():
    running = run("ls /var/ossec/var/run/*.pid 2>/dev/null | wc -l") or "0"
    alert_count = run("wc -l < /var/ossec/logs/alerts/alerts.json 2>/dev/null") or "0"
    return {
        "running_daemons": int(running),
        "total_alerts": int(alert_count),
    }

def get_hactl_status():
    try:
        req = urllib.request.urlopen("http://127.0.0.1:7102/status", timeout=3)
        return json.loads(req.read().decode())
    except:
        return {"state": "unavailable"}

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/" or self.path == "/status":
            data = {
                "processes": get_supervisor_status(),
                "fail2ban": get_fail2ban_status(),
                "crowdsec": get_crowdsec_status(),
                "nginx": get_nginx_status(),
                "wazuh": get_wazuh_status(),
                "hactl": get_hactl_status(),
            }
        elif self.path == "/fail2ban":
            data = get_fail2ban_status()
        elif self.path == "/crowdsec":
            data = get_crowdsec_status()
        elif self.path == "/hactl":
            data = get_hactl_status()
        else:
            data = {"error": "unknown endpoint"}
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, fmt, *args):
        pass

if __name__ == "__main__":
    server = http.server.HTTPServer(("127.0.0.1", 7101), Handler)
    print("Module Status API on :7101", flush=True)
    server.serve_forever()
