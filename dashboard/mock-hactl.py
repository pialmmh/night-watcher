#!/usr/bin/env python3
"""Mock hactl status API for dashboard demo."""
import http.server, json, time

START = time.time()

STATUS = {
    "nodeId": "bdcom1",
    "state": "LEADER",
    "leader": "bdcom1",
    "uptime": "",
    "groups": [
        {
            "id": "sigtran-vip",
            "resources": [
                {"id": "vip-10.255.246.200", "type": "vip", "state": "ACTIVE", "health": "HEALTHY", "reason": "10.255.246.200/32 on eth0"},
                {"id": "sigtran-grameenphone", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "PID 12045, uptime 4h22m"},
                {"id": "sigtran-robi", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "PID 12046, uptime 4h22m"},
                {"id": "sigtran-banglalink", "type": "service", "state": "ACTIVE", "health": "DEGRADED", "reason": "SCTP association flapping"},
                {"id": "sigtran-teletalk", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "PID 12048, uptime 4h22m"},
            ],
            "checks": [
                {"name": "ping-bdcom2", "passed": True, "output": "rtt 0.4ms"},
                {"name": "tcp-consul-8500", "passed": True, "output": "connected in 2ms"},
                {"name": "http-sigtran-gp-health", "passed": True, "output": "HTTP 200 OK"},
                {"name": "http-sigtran-robi-health", "passed": True, "output": "HTTP 200 OK"},
                {"name": "http-sigtran-bl-health", "passed": False, "output": "HTTP 503 - SCTP down"},
                {"name": "http-sigtran-tt-health", "passed": True, "output": "HTTP 200 OK"},
            ],
        },
        {
            "id": "routesphere-core",
            "resources": [
                {"id": "vip-10.255.246.201", "type": "vip", "state": "ACTIVE", "health": "HEALTHY", "reason": "10.255.246.201/32 on eth0"},
                {"id": "routesphere-sms-engine", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "Quarkus PID 8921, port 19999 responding"},
            ],
            "checks": [
                {"name": "http-routesphere-health", "passed": True, "output": "HTTP 200 - ready"},
                {"name": "tcp-kafka-9092", "passed": True, "output": "connected in 1ms"},
                {"name": "tcp-redis-6379", "passed": True, "output": "connected in 1ms"},
                {"name": "tcp-mysql-3306", "passed": True, "output": "connected in 2ms"},
            ],
        },
    ],
}

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        uptime = int(time.time() - START)
        h, m, s = uptime // 3600, (uptime % 3600) // 60, uptime % 60
        STATUS["uptime"] = f"{h}h{m}m{s}s"
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(STATUS).encode())

    def log_message(self, fmt, *args):
        pass

if __name__ == "__main__":
    server = http.server.HTTPServer(("127.0.0.1", 7102), Handler)
    print("Mock hactl API on :7102", flush=True)
    server.serve_forever()
