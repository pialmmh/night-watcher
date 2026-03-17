# Night-Watcher

Unified operations container: security monitoring + HA controller + identity management for Telcobright Routesphere.

## What's In This Repo

| Component | Location | Language | Purpose |
|-----------|----------|----------|---------|
| **ha-controller** | `ha-controller/` | Go 1.21 | Quorum-based health consensus, sentinel failover, generic action sequences |
| **Security bundle** | `common/`, root configs | Python, Bash | Nginx+WAF, Wazuh SIEM, CrowdSec, Fail2Ban, watchdog |
| **Dashboard** | `dashboard/` | React 18 + Vite + MUI | Security dashboard with JWT auth (Keycloak backend) |
| **Identity** | `dashboard/src/auth/`, Keycloak | React, Java | JWT-based auth: login, profile, user management via Keycloak REST APIs |
| **Tenant configs** | `tenants/{tenant}/` | YAML, conf | Per-tenant security + HA config |
| **Design docs** | `docs/` | Markdown | 2FA designs, Keycloak identity architecture |

## Build & Run

```bash
# Build hactl binary locally
cd ha-controller && make build && make test

# Build Docker image (includes everything)
docker build -t telcobright/night-watcher:latest .

# Deploy to tenant server
./deploy.sh btclsms
```

## Project Layout

```
night-watcher/
├── Dockerfile              # 3-stage: modsec-build + go-build + runtime
├── build.sh                # Docker image builder
├── deploy.sh               # Deploy to remote server
├── supervisord.conf        # Process manager (9+ processes)
├── entrypoint.sh           # Container init (config wiring)
│
├── ha-controller/          # Go HA control plane
│   ├── cmd/hactl/main.go   # CLI + API server on :7102
│   ├── internal/api/       # HTTP status + manual failover API
│   ├── internal/config/    # YAML config loader + validation
│   ├── internal/consul/    # Consul client, leader election, KV CAS
│   ├── internal/engine/    # Sentinel-aware reconciliation loop
│   ├── internal/executor/  # Local + SSH command execution
│   ├── internal/healthcheck/ # Ping, TCP, HTTP, Script probes
│   ├── internal/node/      # Node abstraction + fencing
│   ├── internal/resource/  # Resource interface: VIP, Action, Noop
│   ├── internal/sentinel/  # SDOWN/ODOWN consensus + failover coordinator
│   └── configs/            # Sample ha-controller.yml
│
├── dashboard/              # React + Vite + MUI
│   └── src/
│       ├── auth/           # JWT auth: AuthContext, ProtectedRoute, keycloak.js
│       ├── pages/          # Login, Profile, UserManagement, Overview, HA, etc.
│       ├── components/     # Layout (nav with auth), charts, tables
│       └── api/            # OpenSearch API helpers
│
├── common/                 # Shared configs
│   ├── scripts/            # watchdog, log-shipper, module-status-api
│   ├── mysql/schema.sql    # All tables (5 tables, monthly partitions)
│   └── nginx/, modsecurity/, wazuh/
│
├── docs/                   # Design documents
│   ├── keycloak-identity-design.md  # Keycloak + custom UI architecture
│   ├── 2fa-web-design.md           # Nginx-enforced 2FA for web apps
│   └── 2fa-ssh-design.md           # Google Authenticator PAM for SSH
│
└── tenants/
    ├── btclsms/            # BTCL tenant config
    │   ├── tenant.conf
    │   ├── ha-controller.yml
    │   └── nodes.json
    └── bdcom/              # BDCOM tenant config
        ├── ha-controller.yml
        └── nodes.json
```

## Ports

| Port | Service |
|------|---------|
| 80/443 | Nginx (HTTP/HTTPS) |
| 1514/1515 | Wazuh agent |
| 5601 | Wazuh Dashboard |
| 9200 | OpenSearch |
| 55000 | Wazuh API |
| 7100 | Dashboard dev (Vite) |
| 7101 | Module status API |
| 7102 | hactl status API |
| 7104 | Keycloak (proxied via Nginx at /auth/) |

## Supervisord Processes (priority order)

nginx(10), keycloak(11), hactl(15), crowdsec(20), fail2ban(30), wazuh-indexer(35), wazuh-manager(40), wazuh-dashboard(50), watchdog(60), log-shipper(70)

hactl is optional — controlled by `HACTL_ENABLED` env var (default: false).

## HA Controller Architecture

Quorum-based health consensus (Redis Sentinel model):

1. **Every node** (5s tick): run health checks → publish observation to Consul KV
2. **SDOWN**: if checks fail N consecutive times, node marks active node as subjectively down
3. **ODOWN**: if quorum nodes agree SDOWN → objectively down
4. **Coordinator** (Consul lock holder) on ODOWN: select best candidate → execute failover

### Resource Types

| Type | Purpose |
|------|---------|
| `vip` | Floating IP: `ip addr add/del` + gratuitous ARP |
| `action` | Generic shell commands: configurable activate/deactivate/check |
| `noop` | Dummy resource for testing |

### Failover Sequence

On failover from node-A to node-B:
1. CAS increment failover epoch (prevent double failover)
2. **Deactivate on A** (reverse order): notify API → stop service → remove VIP
3. **Activate on B** (forward order): assign VIP → start service → notify API
4. Update active node in Consul KV

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | /status | Full cluster state: activeNode, sdown, odown, observations, groups |
| POST | /failover | Manual failover: `{"targetNode": "node2"}` (coordinator only) |

### Consul KV Schema

```
ha-controller/{cluster}/leader              → coordinator lock
ha-controller/{cluster}/active              → current active node
ha-controller/{cluster}/failover-epoch      → monotonic counter (CAS)
ha-controller/{cluster}/observations/{node} → JSON observation per node
```

## Identity Management

Keycloak backend with custom React UI. No Keycloak UI exposed to users.

### Dashboard Auth Pages

| Page | Route | Access |
|------|-------|--------|
| Login | `/login` | Public |
| Profile | `/profile` | All authenticated users |
| User Management | `/users` | Admin role only |

### JWT Flow

```
Login (username/password) → Keycloak issues JWT → stored in sessionStorage
→ all API requests include Authorization: Bearer <token>
→ auto-refresh before expiry → on 401 redirect to /login
```

### Keycloak Config

- Realm: `night-watcher`
- Client: `nw-dashboard` (public SPA, direct access grants)
- Roles: `admin`, `operator`, `viewer`

## Routesphere Platform Context

Multi-tenant telecom platform (Quarkus 3.26.1 / Java 21) handling SMS, Voice, SIP routing.

### Key Services That Need HA

| Service | Ports | HA status |
|---------|-------|-----------|
| sigtran (SS7 MAP) | 8282 UDP | VIP + action failover via hactl |
| routesphere-core | 19999, 18093 | Future |
| FreeSWITCH | 5060 SIP | Future |

### Production Servers

| Tenant | Server | IP |
|--------|--------|-----|
| btcl | dell-sms-master | 10.246.7.102 |
| btcl | dell-sms-slave | 10.246.7.103 |
| bdcom | bdcom1 | 10.255.246.173 |
| link3 | link3-1 | (link3 net) |

## Guidelines
- Do not git push until asked
- Go 1.21+ for ha-controller
- Dashboard dev port: 7100 (never use 3000 range)
- No new Go dependencies for API (stdlib net/http only)
- MySQL partitions: create all partitions in CREATE TABLE, not ALTER
