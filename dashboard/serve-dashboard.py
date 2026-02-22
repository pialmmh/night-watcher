#!/usr/bin/env python3
"""
Serves the built dashboard + proxies API calls to local backends.
Run on the remote server where OpenSearch/Wazuh are running.

Usage: python3 serve-dashboard.py [port]
  Default port: 7100
"""
import http.server, json, os, ssl, sys, time, urllib.request, urllib.error

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 7100
DIST_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dist")

# Backend targets
PROXY_MAP = {
    "/api/es/":    ("https://127.0.0.1:9200/",  {"Authorization": "Basic YWRtaW46YWRtaW4="}),
    "/api/wazuh/": ("https://127.0.0.1:55000/",  {}),
    "/api/hactl/": ("http://127.0.0.1:7102/",    {}),
    "/api/status/": ("http://127.0.0.1:7101/",   {}),
}

# SSL context that skips verification for self-signed certs
_ctx = ssl.create_default_context()
_ctx.check_hostname = False
_ctx.verify_mode = ssl.CERT_NONE

# ── Mock hactl (in-process, no separate server needed) ───────────────────

HACTL_START = time.time()
HACTL_STATUS = {
    "nodeId": "dell-sms-slave",
    "state": "LEADER",
    "leader": "dell-sms-slave",
    "uptime": "",
    "groups": [
        {
            "id": "sigtran-vip",
            "resources": [
                {"id": "vip-10.10.195.10", "type": "vip", "state": "ACTIVE", "health": "HEALTHY", "reason": "10.10.195.10/24 on lxdbr0"},
                {"id": "sigtran-grameenphone", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "MAP service running"},
                {"id": "sigtran-robi", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "MAP service running"},
                {"id": "sigtran-banglalink", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "MAP service running"},
                {"id": "sigtran-teletalk", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "MAP service running"},
            ],
            "checks": [
                {"name": "ping-dell-sms-master", "passed": True, "output": "rtt 0.5ms"},
                {"name": "tcp-opensearch-9200", "passed": True, "output": "connected in 1ms"},
                {"name": "tcp-wazuh-55000", "passed": True, "output": "connected in 2ms"},
                {"name": "http-sigtran-gp-health", "passed": True, "output": "HTTP 200 OK"},
                {"name": "http-sigtran-robi-health", "passed": True, "output": "HTTP 200 OK"},
            ],
        },
        {
            "id": "routesphere-core",
            "resources": [
                {"id": "vip-10.10.195.20", "type": "vip", "state": "ACTIVE", "health": "HEALTHY", "reason": "10.10.195.20/24 on lxdbr0"},
                {"id": "routesphere-sms-engine", "type": "service", "state": "ACTIVE", "health": "HEALTHY", "reason": "Quarkus running on port 19999"},
            ],
            "checks": [
                {"name": "http-routesphere-health", "passed": True, "output": "HTTP 200 - ready"},
                {"name": "tcp-mysql-3306", "passed": True, "output": "connected in 1ms"},
            ],
        },
    ],
}


def get_hactl_json():
    elapsed = int(time.time() - HACTL_START)
    h, m, s = elapsed // 3600, (elapsed % 3600) // 60, elapsed % 60
    HACTL_STATUS["uptime"] = f"{h}h{m}m{s}s"
    return json.dumps(HACTL_STATUS).encode()


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIST_DIR, **kwargs)

    def do_GET(self):
        return self._handle()

    def do_POST(self):
        return self._handle()

    def _handle(self):
        # Check API proxies
        for prefix, (target, extra_headers) in PROXY_MAP.items():
            if self.path.startswith(prefix):
                # Built-in hactl mock
                if prefix == "/api/hactl/":
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(get_hactl_json())
                    return
                return self._proxy(prefix, target, extra_headers)

        # SPA fallback: if file doesn't exist, serve index.html
        path = self.path.split("?")[0]
        local = os.path.join(DIST_DIR, path.lstrip("/"))
        if not os.path.isfile(local) and not path.startswith("/assets/"):
            self.path = "/index.html"

        return super().do_GET()

    def _proxy(self, prefix, target, extra_headers):
        downstream_path = self.path[len(prefix):]
        url = target + downstream_path

        # Read body for POST
        body = None
        length = self.headers.get("Content-Length")
        if length:
            body = self.rfile.read(int(length))

        req = urllib.request.Request(url, data=body)
        req.method = self.command

        # Forward content-type
        ct = self.headers.get("Content-Type")
        if ct:
            req.add_header("Content-Type", ct)

        # Add extra headers (auth, etc.)
        for k, v in extra_headers.items():
            req.add_header(k, v)

        try:
            resp = urllib.request.urlopen(req, context=_ctx, timeout=30)
            data = resp.read()
            self.send_response(resp.status)
            self.send_header("Content-Type", resp.headers.get("Content-Type", "application/json"))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(data)
        except urllib.error.HTTPError as e:
            data = e.read()
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())

    def log_message(self, fmt, *args):
        sys.stderr.write(f"[dashboard] {args[0]}\n")


if __name__ == "__main__":
    server = http.server.HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Dashboard serving on http://0.0.0.0:{PORT}", flush=True)
    print(f"  Static files: {DIST_DIR}", flush=True)
    print(f"  API proxy: /api/es/ -> https://127.0.0.1:9200/", flush=True)
    print(f"  API proxy: /api/wazuh/ -> https://127.0.0.1:55000/", flush=True)
    print(f"  API proxy: /api/status/ -> http://127.0.0.1:7101/", flush=True)
    print(f"  HA mock:   /api/hactl/ -> built-in mock", flush=True)
    server.serve_forever()
