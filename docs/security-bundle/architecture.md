# Security Bundle — Architecture

## Goal

All-in-one security monitoring and reverse proxy container for each Routesphere tenant deployment. One night-watcher container per physical server cluster.

## Components

| Component | Purpose | Config |
|-----------|---------|--------|
| **Nginx + ModSecurity** | Reverse proxy + WAF (OWASP CRS) | `tenants/{t}/nginx/`, `tenants/{t}/modsecurity/` |
| **CrowdSec** | Collaborative IP reputation + firewall bouncer | `tenants/{t}/crowdsec/` |
| **Fail2Ban** | Log-based IP banning | `tenants/{t}/fail2ban/` |
| **Wazuh Manager** | SIEM: log collection, analysis, alerting | `tenants/{t}/wazuh/` |
| **Wazuh Indexer** | OpenSearch for Wazuh data (384MB heap) | Auto-configured |
| **Wazuh Dashboard** | Kibana-like UI on port 5601 | Auto-configured |
| **Watchdog** | Health monitoring of backend services | `tenants/{t}/watchdog/services.conf` |
| **Log Shipper** | Tails Wazuh alerts → MySQL | Python, uses MySQL creds from tenant.conf |
| **Module Status API** | HTTP JSON on port 7101 for process status | Python |

## Process Management

Supervisord manages all processes with priority-based startup:

```
nginx(10) → consul(12) → hactl(15) → crowdsec(20) → fail2ban(30)
→ wazuh-indexer(35) → wazuh-manager(40) → wazuh-dashboard(50)
→ module-status-api(55) → watchdog(60) → log-shipper(70)
```

## Container Init (entrypoint.sh)

1. Read `tenant.conf` (sources env vars)
2. Copy tenant Nginx configs → `/etc/nginx/`
3. Copy common Nginx snippets (don't overwrite tenant-specific)
4. Copy tenant ModSecurity exclusions → `/etc/nginx/modsecurity/`
5. Configure CrowdSec (acquis, whitelist, collections)
6. Configure Fail2Ban (jail.local, custom filters)
7. Merge Wazuh base + tenant ossec.conf
8. Generate Wazuh TLS certs (root CA, indexer, admin, dashboard)
9. Set MySQL env vars for child processes → `/etc/security-bundle.env`
10. Configure HA controller (if `hactl_enabled=true`)
11. Set `vm.max_map_count` for OpenSearch
12. Validate Nginx config
13. Start supervisord
14. Wait for OpenSearch → run security init

## Tenant Config (tenant.conf)

```bash
tenant_name=btclsms
ssh_tenant=btcl              # matches ssh-automation/servers/<tenant>
ssh_server=dell-sms-master   # primary deploy target
deploy_user=telcobright

mysql_host=10.10.196.10
mysql_port=3306
mysql_user=security_bundle
mysql_pass=changeme
mysql_db=security_monitoring

wazuh_api_user=admin
wazuh_api_pass=changeme

hactl_enabled=true
hactl_node_id=btcl-nw-1

container_memory=2g
container_ports=80,443,1514,1515,5601,9000
```

## MySQL Schema

5 tables in `security_monitoring` database, all with monthly partitions (create all partitions in CREATE TABLE):
- `wazuh_alerts` — Wazuh alert log
- `waf_events` — ModSecurity WAF events
- `fail2ban_events` — Ban/unban events
- `crowdsec_decisions` — CrowdSec IP decisions
- `watchdog_status` — Backend health check history

Schema file: `common/mysql/schema.sql`

## Ports

| Port | Service | Exposed |
|------|---------|---------|
| 80/443 | Nginx | Yes |
| 1514/1515 | Wazuh agent registration | Yes |
| 5601 | Wazuh Dashboard | Yes |
| 9200 | OpenSearch API | Internal |
| 55000 | Wazuh Manager API | Internal |
| 7100 | Dashboard dev (Vite) | Dev only |
| 7101 | Module status API | Internal |
| 7102 | hactl status API | Internal |

## Per-Tenant File Structure

```
tenants/btclsms/
├── tenant.conf                    # Sourced by entrypoint.sh
├── ha-controller.yml              # hactl cluster config
├── nodes.json                     # Dashboard node list for HA page
├── nginx/
│   ├── nginx.conf                 # Main Nginx config
│   ├── proxy_params               # Shared proxy headers
│   ├── sites-enabled/             # Virtual hosts
│   └── snippets/                  # SSL, security headers
├── modsecurity/
│   └── crs-exclusions.conf        # Tenant-specific WAF exclusions
├── crowdsec/
│   ├── acquis.yaml                # Log sources
│   ├── collections.txt            # CrowdSec collections to install
│   └── whitelist.yaml             # IP whitelist
├── fail2ban/
│   ├── jail.local                 # Jail definitions
│   └── filter-*.conf              # Custom filters
├── wazuh/
│   └── ossec-tenant.conf          # Merged with base ossec.conf
└── watchdog/
    └── services.conf              # Backend health check targets
```

## Deployment

```bash
# Deploy to tenant server
./deploy.sh btclsms

# Build Docker image
docker build -t telcobright/night-watcher:latest .

# Launch (per-node config passed via environment)
lxc launch ... --config environment.HACTL_ENABLED=true \
               --config environment.HACTL_NODE_ID=btcl-nw-1
```

## Network

Night-watcher runs in an LXC container on each server:
- Gets an IP on lxdbr0 (e.g., 10.10.195.200)
- Also gets the VIP (e.g., 10.255.246.175) when it's the active HA node
- SSHes to the host (via lxdbr0 gateway) to execute VIP and service management commands
- Consul runs inside the container for leader election (cluster-wide)
