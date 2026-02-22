# Night-Watcher

Unified operations container: security monitoring + HA controller for Telcobright Routesphere.

## What's In This Repo

| Component | Location | Language | Purpose |
|-----------|----------|----------|---------|
| **ha-controller** | `ha-controller/` | Go 1.21 | Consul-based leader election, VIP failover, health checks |
| **Security bundle** | `common/`, `dashboard/`, root configs | Python, React, Bash | Nginx+WAF, Wazuh SIEM, CrowdSec, Fail2Ban, watchdog |
| **Tenant configs** | `tenants/{tenant}/` | YAML, conf | Per-tenant security + HA config |

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
├── supervisord.conf        # Process manager (9 processes)
├── entrypoint.sh           # Container init (config wiring)
├── ha-controller/          # Go HA control plane
│   ├── cmd/hactl/main.go   # CLI + API server on :7102
│   ├── internal/api/       # HTTP status API
│   ├── internal/config/    # YAML config loader
│   ├── internal/consul/    # Consul client + leader election
│   ├── internal/engine/    # Reconciliation loop
│   ├── internal/executor/  # Local + SSH command execution
│   ├── internal/healthcheck/ # Ping, TCP, HTTP, Script probes
│   ├── internal/node/      # Node abstraction + fencing
│   ├── internal/resource/  # Resource interface + VIP impl
│   └── configs/            # Sample ha-controller.yml
├── common/                 # Shared configs
│   ├── scripts/            # watchdog, log-shipper, module-status-api
│   ├── mysql/schema.sql    # All tables (5 tables, monthly partitions)
│   ├── nginx/, modsecurity/, wazuh/
├── dashboard/              # React + Vite + MUI
│   └── src/pages/HaCluster.jsx  # HA cluster status page
└── tenants/
    └── btclsms/            # Per-tenant config
        ├── tenant.conf     # Tenant settings (incl hactl_enabled)
        └── ha-controller.yml # HA cluster config
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

## Supervisord Processes (priority order)

nginx(10), hactl(15), crowdsec(20), fail2ban(30), wazuh-indexer(35), wazuh-manager(40), wazuh-dashboard(50), watchdog(60), log-shipper(70)

hactl is optional — controlled by `HACTL_ENABLED` env var (default: false).

## Routesphere Platform Context

Multi-tenant telecom platform (Quarkus 3.26.1 / Java 21) handling SMS, Voice, SIP routing.

### Key Services That Need HA

| Service | Ports | HA status |
|---------|-------|-----------|
| sigtran (SS7 MAP) | 8282 UDP | VIP failover via hactl |
| routesphere-core | 19999, 18093 | Future |
| FreeSWITCH | 5060 SIP | Future |

### Production Servers

| Tenant | Server | IP |
|--------|--------|-----|
| btcl | dell-sms-master | 10.246.7.102 |
| bdcom | bdcom1 | 10.255.246.173 |
| link3 | link3-1 | (link3 net) |

## Guidelines
- Do not git push until asked
- Go 1.21+ for ha-controller
- Dashboard dev port: 7100 (never use 3000 range)
- No new Go dependencies for API (stdlib net/http only)
- MySQL partitions: create all partitions in CREATE TABLE, not ALTER
