Night-Watcher Migration Instructions

Task for Agent

Merge the security-bundle project into night-watcher to create a single deployable Docker artifact that combines:
- Security bundle (Nginx/WAF, Wazuh SIEM, CrowdSec, Fail2Ban, watchdog, log-shipper, React dashboard)
- HA controller (Go binary for Consul-based leader election, VIP failover, health checks)

The React security dashboard becomes the unified GUI for both security and HA cluster monitoring.

Source Locations

- Security-bundle source: /home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/webappdeploy/security-bundle/
- Night-watcher destination: /home/mustafa/telcobright-projects/routesphere/night-watcher/
- Night-watcher has its own git repo (separate from routesphere parent)

Target Directory Structure

night-watcher/                          # Separate git repo
├── CLAUDE.md                           # Updated project instructions
├── PROJECT.md                          # Updated project description
├── Dockerfile                          # Extended: + Go build stage for hactl
├── build.sh                            # From security-bundle
├── deploy.sh                           # From security-bundle, extended
├── supervisord.conf                    # + hactl process entry
├── entrypoint.sh                       # + ha-controller config handling
├── .gitignore                          # From security-bundle
├── ha-controller/                      # Go source (already here)
│   ├── cmd/hactl/main.go              # MODIFY: start HTTP API server
│   ├── internal/
│   │   ├── api/                        # NEW: HTTP status API for dashboard
│   │   │   └── server.go
│   │   ├── config/config.go
│   │   ├── consul/client.go, leader.go
│   │   ├── engine/engine.go
│   │   ├── executor/executor.go, local.go, ssh.go
│   │   ├── healthcheck/check.go
│   │   ├── node/node.go
│   │   └── resource/group.go, resource.go, vip.go
│   ├── go.mod, go.sum
│   ├── Makefile
│   └── configs/ha-controller.yml
├── common/                             # From security-bundle (copy as-is)
│   ├── nginx/snippets/
│   ├── modsecurity/
│   ├── wazuh/
│   ├── mysql/schema.sql                # MODIFY: + ha_events table
│   └── scripts/
│       ├── watchdog.sh
│       ├── log-shipper.py
│       ├── module-status-api.py        # MODIFY: + /hactl endpoint
│       ├── install-agent.sh
│       └── wazuh-manager-wrapper.sh
├── dashboard/                          # From security-bundle, extended
│   ├── src/
│   │   ├── App.jsx                     # MODIFY: add /ha route
│   │   ├── main.jsx
│   │   ├── api/opensearch.js
│   │   ├── utils/sanitize.js
│   │   ├── components/
│   │   │   ├── Layout.jsx              # MODIFY: add "HA Cluster" nav item
│   │   │   ├── AlertDetail.jsx
│   │   │   ├── AlertTable.jsx
│   │   │   ├── AlertTrend.jsx
│   │   │   ├── DateRangePicker.jsx
│   │   │   ├── MitreTags.jsx
│   │   │   ├── SeverityCards.jsx
│   │   │   ├── TopIPs.jsx
│   │   │   ├── TopRules.jsx
│   │   │   ├── WafEvents.jsx
│   │   │   └── WatchdogStatus.jsx
│   │   └── pages/
│   │       ├── Overview.jsx
│   │       ├── Logs.jsx
│   │       ├── Modules.jsx
│   │       ├── SecurityEvents.jsx
│   │       ├── Waf.jsx
│   │       ├── Watchdog.jsx
│   │       ├── Network.jsx
│   │       └── HaCluster.jsx          # NEW: HA cluster status page
│   ├── dist/                           # Pre-built assets
│   ├── index.html
│   ├── package.json
│   ├── package-lock.json
│   ├── public/favicon.svg
│   └── vite.config.js                  # MODIFY: add /api/hactl proxy
└── tenants/
└── btclsms/
├── tenant.conf                 # MODIFY: + hactl config vars
├── ha-controller.yml           # NEW: per-tenant HA config
├── tenant.conf.example
├── ACCESS_INFO.md
├── crowdsec/
├── fail2ban/
├── modsecurity/
├── nginx/
├── watchdog/
└── wazuh/

 ---
Step 1: Copy security-bundle files into night-watcher

Copy these from routesphere-core/tools/webappdeploy/security-bundle/ into night-watcher/:

SRC=/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/webappdeploy/security-bundle
DST=/home/mustafa/telcobright-projects/routesphere/night-watcher

# Root files
cp "$SRC/Dockerfile" "$SRC/build.sh" "$SRC/deploy.sh" "$SRC/supervisord.conf" "$SRC/entrypoint.sh" "$SRC/.gitignore" "$SRC/README.md" "$DST/"

# Directories (exclude node_modules from dashboard)
cp -r "$SRC/common" "$DST/"
rsync -a --exclude='node_modules' "$SRC/dashboard/" "$DST/dashboard/"
cp -r "$SRC/tenants" "$DST/"

Do NOT delete the source security-bundle yet (user can do that separately).

 ---
Step 2: Extend Dockerfile with Go build stage

The existing Dockerfile has 2 stages: modsec-build (FROM debian:12-slim) and runtime (FROM debian:12-slim).

Insert a Go build stage between them, and add COPY in the runtime stage.

Current Dockerfile structure:

FROM debian:12-slim AS modsec-build    # Stage 1 (lines 5-43)
...builds ModSecurity, CRS, nginx connector...

FROM debian:12-slim                     # Stage 2 (line 46+)
...runtime with all packages, copies from modsec-build...
EXPOSE 80 443 1514 1515 5601 9200 55000
ENTRYPOINT ["/entrypoint.sh"]

Add after line 43 (after modsec-build stage, before runtime stage):

# ── Stage 2: Build ha-controller ─────────────────────────────────────────────
FROM golang:1.23-bookworm AS go-build
WORKDIR /build
COPY ha-controller/go.mod ha-controller/go.sum ./
RUN go mod download
COPY ha-controller/ .
RUN make build

Add in runtime stage (after the scripts COPY, around line 139):

# ── Copy hactl binary from Go build stage ─────────────────────────────────────
COPY --from=go-build /build/bin/hactl /usr/local/bin/hactl

Add port 7102 to EXPOSE line:

EXPOSE 80 443 1514 1515 5601 9200 55000 7102

 ---
Step 3: Add hactl to supervisord.conf

The existing supervisord.conf has 8 processes: nginx(10), crowdsec(20), fail2ban(30), wazuh-indexer(35), wazuh-manager(40), wazuh-dashboard(50), watchdog(60), log-shipper(70).

Append this block after the nginx section (priority 15, between nginx=10 and crowdsec=20):

; ── HA Controller (Consul leader election + VIP failover) ────────────
[program:hactl]
command=/usr/local/bin/hactl --config /config/ha-controller.yml --node %(ENV_HACTL_NODE_ID)s --log-level info
autostart=%(ENV_HACTL_ENABLED)s
autorestart=true
priority=15
startsecs=5
stdout_logfile=/var/log/security-bundle/hactl-stdout.log
stderr_logfile=/var/log/security-bundle/hactl-stderr.log
stdout_logfile_maxbytes=5MB
stderr_logfile_maxbytes=5MB

- HACTL_ENABLED env var controls autostart (default: false — set by entrypoint.sh)
- HACTL_NODE_ID env var is this node's name (e.g. "bdcom1")

 ---
Step 4: Extend entrypoint.sh

Add this block after step 10 (watchdog services config) and before step 11 (vm.max_map_count):

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

Also add HACTL vars to the env file section (step 9):

# Add these lines to the cat > /etc/security-bundle.env block:
HACTL_ENABLED=${HACTL_ENABLED:-false}
HACTL_NODE_ID=${HACTL_NODE_ID:-none}

 ---
Step 5: Add HTTP status API to hactl

5a. Create ha-controller/internal/api/server.go

This is a small HTTP server on port 7102 exposing HA state for the dashboard. Use only stdlib net/http (no new dependencies).

package api

import (
"encoding/json
"fmt
"log/slog
"net/http
"time

        "github.com/telcobright/ha-controller/internal/engine
        "github.com/telcobright/ha-controller/internal/resource
)

// StatusResponse is the JSON shape returned by GET /status.
type StatusResponse struct {
NodeID  string        `json:"nodeId"
        State   string        `json:"state"
Leader  string        `json:"leader"
        Uptime  string        `json:"uptime"
Groups  []GroupStatus `json:"groups"
}

type GroupStatus struct {
ID        string           `json:"id"
        Resources []ResourceStatus `json:"resources"
Checks    []CheckStatus    `json:"checks"
}

type ResourceStatus struct {
ID     string `json:"id"
        Type   string `json:"type"
State  string `json:"state"
        Health string `json:"health"
}

type CheckStatus struct {
Name   string `json:"name"
        Passed bool   `json:"passed"
Output string `json:"output"
}

// Server serves the hactl status API.
type Server struct {
engine    *engine.Engin
nodeID    strin
startTime time.Tim
logger    *slog.Logge
}

// NewServer creates the API server.
func NewServer(eng *engine.Engine, nodeID string, logger *slog.Logger) *Server {
return &Server
engine:    en
nodeID:    nodeI
startTime: time.Now(
logger:    logger.With("component", "api"

}

// Start runs the HTTP server on the given port. Blocks until error.
func (s *Server) Start(port int) error {
mux := http.NewServeMux(
mux.HandleFunc("/status", s.handleStatus
mux.HandleFunc("/", s.handleStatus) // convenienc

        addr := fmt.Sprintf(":%d", port
        s.logger.Info("starting status API", "addr", addr
        return http.ListenAndServe(addr, mux
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
resp := StatusResponse
NodeID: s.nodeI
State:  s.engine.State().String(
Leader: s.engine.CurrentLeader(
Uptime: time.Since(s.startTime).Truncate(time.Second).String(
Groups: s.buildGroupStatus(


        w.Header().Set("Content-Type", "application/json"
        w.Header().Set("Access-Control-Allow-Origin", "*"
        json.NewEncoder(w).Encode(resp
}

func (s *Server) buildGroupStatus() []GroupStatus {
var groups []GroupStatu
for _, g := range s.engine.Groups()
gs := GroupStatus{ID: g.ID(

                // Resourc
                for _, res := range g.Resources()
                        rs := ResourceStat
                                ID:    res.I
                                Type:  res.Typ
                                State: res.Stat

                        health := res.Chec
                        rs.Health = string(health.Stat
                        gs.Resources = append(gs.Resources,


                // Health chec
                results := g.CheckAll
                for id, result := range results
                        cs := CheckStat
                                Name:
                                Passed: result.Status == resource.HealthHeal
                                Output: result.Rea

                        gs.Checks = append(gs.Checks,


                groups = append(groups, g

        return group
}

5b. Expose required methods on Engine

The API server needs access to engine.Groups() and engine.CurrentLeader().

Add to ha-controller/internal/engine/engine.go:

// Groups returns the resource groups.
func (e *Engine) Groups() []*resource.ResourceGroup {
return e.group
}

// CurrentLeader returns the current leader node ID.
func (e *Engine) CurrentLeader() string {
leader, _ := e.election.CurrentLeader(
return leade
}

5c. Expose required methods on resource types

Add to ha-controller/internal/resource/group.go:

// Resources returns the resources in this group.
func (g *ResourceGroup) Resources() []Resource {
return g.resource
}

Ensure the Resource interface in resource.go includes:

type Resource interface {
ID() strin
Type() strin
State() string  // "active", "standby", "unknown
Activate() erro
Deactivate() erro
Check() HealthResul
}

If State() and Type() aren't on the interface yet, add them + implementations on VipResource.

5d. Wire API server into cmd/hactl/main.go

After creating the engine (line ~132), before the signal handling:

// Import the api package
import "github.com/telcobright/ha-controller/internal/api"

// After engine creation, start API server in a goroutine:
apiServer := api.NewServer(eng, nodeID, logger)
go func() {
if err := apiServer.Start(7102); err != nil {
logger.Error("API server failed", "err", err)
}
}()

 ---
Step 6: Add HA Cluster page to React dashboard

6a. Create dashboard/src/pages/HaCluster.jsx

New page showing:
- Cluster State card: leader/follower badge, node ID, uptime, leader name
- Resource Groups table: VIP ID, type, state (active/standby), health (healthy/unhealthy)
- Health Checks grid: check name, pass/fail icon, last output
- Auto-refresh every 10 seconds

Data source: GET /api/hactl/status (proxied to hactl on port 7102)

Use the same MUI component patterns as existing pages (Box, Card, Typography, Table, Chip). Look at Watchdog.jsx and Modules.jsx for reference styling.

// Fetch pattern:
const fetchStatus = async () => {
try {
const res = await fetch('/api/hactl/status');
if (res.ok) setData(await res.json());
} catch (e) { /* ignore */ }
};

useEffect(() => {
fetchStatus();
const interval = setInterval(fetchStatus, 10000);
return () => clearInterval(interval);
}, []);

6b. Modify dashboard/src/App.jsx

Current routes (line 33-40):
<Route path="/" element={<Overview />} />
<Route path="/logs" element={<Logs />} />
<Route path="/modules" element={<Modules />} />
<Route path="/security" element={<SecurityEvents />} />
<Route path="/waf" element={<Waf />} />
<Route path="/watchdog" element={<Watchdog />} />
<Route path="/network" element={<Network />} />

Add after the /network route:
<Route path="/ha" element={<HaCluster />} />

Add import at top:
import HaCluster from './pages/HaCluster';

6c. Modify dashboard/src/components/Layout.jsx

Add to imports:
import DeviceHubIcon from '@mui/icons-material/DeviceHub';

Add to NAV_ITEMS array (after the Network entry):
{ label: 'HA Cluster', path: '/ha', icon: <DeviceHubIcon /> },

6d. Modify dashboard/vite.config.js

Add proxy for hactl API (in the server.proxy section):

'/api/hactl': {
target: 'http://127.0.0.1:7102',
changeOrigin: true,
rewrite: (path) => path.replace(/^\/api\/hactl/, ''),
},

6e. Modify common/scripts/module-status-api.py

Add /hactl endpoint that proxies to localhost:7102/status.

In the Handler.do_GET method, add:

elif self.path == "/hactl":
# Proxy to hactl status API
import urllib.request
try:
req = urllib.request.urlopen("http://127.0.0.1:7102/status", timeout=3)
data = json.loads(req.read().decode())
except:
data = {"error": "hactl not available"}

Also update the main /status endpoint to include hactl status:
# In the "/" or "/status" branch, add:
"hactl": get_hactl_status(),

With helper:
def get_hactl_status():
import urllib.request
try:
req = urllib.request.urlopen("http://127.0.0.1:7102/status", timeout=3)
return json.loads(req.read().decode())
except:
return {"state": "unavailable"}

 ---
Step 7: Extend tenant.conf and MySQL schema

7a. Add to tenants/btclsms/tenant.conf:

# HA Controller
hactl_enabled=false
hactl_node_id=bdcom1

7b. Create tenants/btclsms/ha-controller.yml

Copy from night-watcher/ha-controller/configs/ha-controller.yml (already contains bdcom cluster config).

7c. Add ha_events table to common/mysql/schema.sql

Append after the nginx_hourly_stats table:

-- ── HA Controller Events ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ha_events (
id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
tenant VARCHAR(64) NOT NULL,
event_timestamp DATETIME NOT NULL,
node_id VARCHAR(50) NOT NULL,
event_type ENUM('promoted','demoted','failover','fence','vip_activated','vip_deactivated') NOT NULL,
detail TEXT,
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (id, event_timestamp),
INDEX idx_tenant_ts (tenant, event_timestamp),
INDEX idx_node (node_id, event_timestamp),
INDEX idx_event_type (event_type, event_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (TO_DAYS(event_timestamp)) (
PARTITION p2026_01 VALUES LESS THAN (TO_DAYS('2026-02-01')),
PARTITION p2026_02 VALUES LESS THAN (TO_DAYS('2026-03-01')),
PARTITION p2026_03 VALUES LESS THAN (TO_DAYS('2026-04-01')),
PARTITION p2026_04 VALUES LESS THAN (TO_DAYS('2026-05-01')),
PARTITION p2026_05 VALUES LESS THAN (TO_DAYS('2026-06-01')),
PARTITION p2026_06 VALUES LESS THAN (TO_DAYS('2026-07-01')),
PARTITION p2026_07 VALUES LESS THAN (TO_DAYS('2026-08-01')),
PARTITION p2026_08 VALUES LESS THAN (TO_DAYS('2026-09-01')),
PARTITION p2026_09 VALUES LESS THAN (TO_DAYS('2026-10-01')),
PARTITION p2026_10 VALUES LESS THAN (TO_DAYS('2026-11-01')),
PARTITION p2026_11 VALUES LESS THAN (TO_DAYS('2026-12-01')),
PARTITION p2026_12 VALUES LESS THAN (TO_DAYS('2027-01-01')),
PARTITION p_future VALUES LESS THAN MAXVALUE
);

 ---
Step 8: Update CLAUDE.md

Update night-watcher/CLAUDE.md to reflect the merged project — it now includes both security modules and HA controller. Add build/deploy instructions for the Docker container.

 ---
Key Reference: Existing Code Patterns

Engine state machine (engine.go lines 16-36)

StateInit → StateFollower → StateLeader → StateStopping
- reconcile() runs every 5s, calls election.TryAcquire()
- On promotion: onBecomeLeader() → activates all resource groups
- On demotion: onLoseLeadership() → deactivates all resource groups

Resource interface (resource/resource.go)

- Activate() / Deactivate() / Check() → HealthResult
- VIP: uses ip addr add/del + gratuitous ARP via local executor

Module Status API (module-status-api.py)

- Listens on 127.0.0.1:7101
- Endpoints: /status (all modules), /fail2ban, /crowdsec
- Dashboard dev proxy at port 7100

Dashboard (dashboard/)

- React + Vite + MUI + Recharts
- Dev port: 7100, proxies to OpenSearch (9200) and Wazuh API (55000)
- Pre-built dist/ included for production (served by nginx)
- Nav items defined in Layout.jsx NAV_ITEMS array

Supervisord processes and priorities

nginx=10, hactl=15(NEW), crowdsec=20, fail2ban=30, wazuh-indexer=35, wazuh-manager=40, wazuh-dashboard=50, watchdog=60, log-shipper=70

 ---
Verification

1. cd night-watcher/ha-controller && make build — hactl binary compiles with new API package
2. cd night-watcher && docker build -t telcobright/night-watcher:latest . — Docker image builds (modsec + go + runtime stages)
3. docker run --rm telcobright/night-watcher:latest supervisorctl status — all processes listed (hactl shows STOPPED if HACTL_ENABLED=false)
4. curl localhost:7102/status — hactl API returns cluster state JSON
5. Dashboard: navigate to /ha — shows HA cluster status page
6. cd night-watcher/dashboard && npm run build — dashboard builds with new HaCluster page

Guidelines

- Night-watcher has its own git repo — commit all changes there
- Do not git push until asked
- Go version: 1.23+ (match ha-controller CLAUDE.md)
- No new Go dependencies for the API (use stdlib net/http)
- Dashboard port: 7100 (dev), hactl API port: 7102
- Use JDK 21 for any Java work (not relevant here, but noted in project rules)
