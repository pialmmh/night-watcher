# Night-Watcher LXC Container

## Image Spec

| Field | Value |
|-------|-------|
| Base | Debian 12 |
| IP | 10.10.19x.200 |
| Memory | 2GB |
| CPU | 2 cores |
| Image path | `orchestrix/images/lxc/night-watcher-v.1.0.0/` |
| Image exists | Yes (built, tarball in `generated/artifact/`) |

## What's Inside

11 processes managed by supervisord:

| Process | Priority | Port | Purpose |
|---------|----------|------|---------|
| nginx | 10 | 80/443 | Reverse proxy + ModSecurity WAF |
| consul | 12 | 8500 | HA cluster coordination |
| hactl | 15 | 7102 | Sentinel-based HA controller (Go) |
| crowdsec | 20 | — | IP reputation + firewall bouncer |
| fail2ban | 30 | — | Log-based IP banning |
| wazuh-indexer | 35 | 9200 | OpenSearch (384MB heap) |
| wazuh-manager | 40 | 1514/55000 | SIEM agent manager |
| wazuh-dashboard | 50 | 5601 | Kibana-like UI |
| module-status-api | 55 | 7101 | Process status JSON API |
| watchdog | 60 | — | Backend health monitoring |
| log-shipper | 70 | — | Wazuh alerts → MySQL |

## Launch Config

```bash
CONTAINER_NAME="night-watcher-btcl"
BASE_IMAGE="night-watcher-base-v.1.0.0"
BRIDGE_NAME="lxdbr0"
CONTAINER_IP="10.10.195.200/24"
GATEWAY_IP="10.10.195.1"
MEMORY_LIMIT="2GB"
CPU_LIMIT="2"
TENANT_ID="btclsms"
TENANT_NAME="BTCL SMS"
MYSQL_HOST="10.10.196.10"
MYSQL_PORT="3306"
MYSQL_USER="security_bundle"
MYSQL_PASS="changeme"
MYSQL_DB="security_monitoring"
HACTL_ENABLED="true"
HACTL_NODE_ID="btcl-nw-1"
CONSUL_ADDRESS="127.0.0.1:8500"
```

## Existing Build Script

```
orchestrix/images/lxc/night-watcher-v.1.0.0/
├── build/
│   ├── build.conf     ← IMAGE_NAME, VERSION, BASE_IMAGE, source paths
│   ├── build.sh       ← 10-step: Debian 12 → Nginx+ModSec → hactl → Wazuh → CrowdSec → tarball
│   └── files/         ← config templates
└── generated/
    ├── launch.sh      ← ./launch.sh <config-file>
    ├── sample.conf    ← all parameters
    └── artifact/
        └── night-watcher-v1-20260223-1837.tar.gz
```

## Tenant Configs

Per-tenant configs in `night-watcher/tenants/<tenant>/`:

```
tenants/btclsms/
├── tenant.conf              ← sourced by entrypoint.sh
├── ha-controller.yml        ← hactl cluster config (3 nodes, sentinel, resources)
├── nodes.json               ← dashboard HA page node list
├── nginx/                   ← sites-enabled, proxy_params, snippets
├── modsecurity/             ← CRS exclusions
├── crowdsec/                ← acquis, whitelist, collections
├── fail2ban/                ← jail.local, custom filters
├── wazuh/                   ← ossec-tenant.conf
└── watchdog/                ← services.conf (health check targets)
```

## Multi-Node Deployment

Each server in the cluster runs its own night-watcher container with a different `HACTL_NODE_ID`:

```
dell-sms-master  → night-watcher-btcl  HACTL_NODE_ID=btcl-nw-1 (priority 1)
dell-sms-slave   → night-watcher-btcl  HACTL_NODE_ID=btcl-nw-2 (priority 2)
sbc1             → night-watcher-btcl  HACTL_NODE_ID=btcl-nw-3 (priority 3)
```

All share the same `ha-controller.yml` config. hactl uses Consul for leader election and health consensus.

## HA Controller (hactl)

Sentinel-based quorum failover:
- All nodes health-check the active node
- SDOWN after N consecutive failures
- ODOWN when quorum agrees
- Coordinator executes failover: deactivate old → activate new
- Resources: VIP (`ip addr add/del`), action (generic shell commands)
- API: GET /status, POST /failover

Full docs: `docs/ha-controller/architecture.md`
