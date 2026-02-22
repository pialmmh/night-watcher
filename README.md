# Security Bundle

All-in-one Docker container: Nginx reverse proxy + WAF + monitoring + SIEM.

## What's Inside

| Process | Purpose | RAM Budget |
|---------|---------|------------|
| Nginx + ModSecurity 3 | Reverse proxy + WAF (OWASP CRS) | ~80 MB |
| CrowdSec + nginx bouncer | Community IP reputation + rate limiting | ~80 MB |
| Fail2Ban | Log-based IP banning | ~40 MB |
| Wazuh Manager | Log analysis, alerts, FIM, rootkit detection | ~350 MB |
| Wazuh Indexer (OpenSearch) | Alert storage + indexing | ~450 MB |
| Wazuh Dashboard | Web UI for security visibility | ~300 MB |
| Watchdog | Backend health monitoring + nginx failover | ~10 MB |
| Log Shipper | Tails alert logs → inserts into MySQL | ~20 MB |
| **Total** | | **~1.3 GB** (fits 2 GB limit) |

## Quick Start

```bash
# 1. Build the image
./build.sh

# 2. Deploy to a tenant
./deploy.sh btclsms
```

## Architecture

```
                Internet
                   │
             ┌─────┴─────┐
             │  CrowdSec  │  ← IP reputation + rate limit + community blocklists
             └─────┬─────┘
                   │
             ┌─────┴─────┐
             │   Nginx    │  ← SSL termination + reverse proxy
             │ ModSecurity│  ← OWASP CRS (SQLi, XSS, RCE, LFI...)
             └─────┬─────┘
                   │
             ┌─────┴─────┐
             │  Fail2Ban  │  ← Bans repeat offenders from ModSecurity/nginx logs
             └─────┬─────┘
                   │
              Applications
                   │
        ┌──────────┼──────────┐
        │          │          │
     Wazuh      Watchdog   Log Shipper
    (SIEM)    (health/      (→ MySQL)
              failover)
```

## Directory Structure

```
security-bundle/
├── Dockerfile                    # Container image definition
├── build.sh                      # Build Docker image
├── deploy.sh                     # Deploy to tenant server
├── supervisord.conf              # Process manager
├── entrypoint.sh                 # Container startup (reads tenant config)
├── common/                       # Shared across all tenants
│   ├── nginx/snippets/           # proxy_headers, modsecurity, cache, acl
│   ├── modsecurity/              # Base WAF config + CRS include chain
│   ├── wazuh/                    # Base ossec.conf + custom decoders/rules
│   ├── scripts/                  # watchdog.sh, log-shipper.py, install-agent.sh
│   └── mysql/schema.sql          # Table definitions (partitioned by month)
└── tenants/
    └── btclsms/                  # Per-tenant configs
        ├── tenant.conf           # SSH, MySQL, container settings
        ├── nginx/                # Site configs, SSL params
        ├── modsecurity/          # CRS exclusions
        ├── crowdsec/             # Whitelist, collections
        ├── fail2ban/             # Jail config, custom filters
        ├── wazuh/                # Tenant-specific ossec config
        └── watchdog/             # Backend services to monitor
```

## Adding a New Tenant

```bash
# 1. Copy existing tenant
cp -r tenants/btclsms tenants/newclient

# 2. Edit tenant.conf (SSH, MySQL, ports)
vim tenants/newclient/tenant.conf

# 3. Replace nginx site configs
vim tenants/newclient/nginx/sites-enabled/default

# 4. Update modsecurity exclusions
vim tenants/newclient/modsecurity/crs-exclusions.conf

# 5. Update watchdog services
vim tenants/newclient/watchdog/services.conf

# 6. Deploy
./deploy.sh newclient
```

## Host Prerequisites

Before deploying, the host server needs:

```bash
# Docker installed
docker --version

# vm.max_map_count for OpenSearch (deploy.sh sets this automatically)
sysctl -w vm.max_map_count=262144

# SSL certs in /etc/nginx/ssl/ (mounted into container)
ls /etc/nginx/ssl/
```

## Verification

```bash
# Check all processes
docker exec security-bundle supervisorctl status

# Check nginx
curl -sk https://localhost/ -o /dev/null -w '%{http_code}'

# Check Wazuh dashboard
curl -sk https://localhost:5601/

# Check Wazuh API
curl -sk -u admin:changeme https://localhost:55000/

# View logs
docker logs security-bundle --tail 50
tail -f /var/log/security-bundle/watchdog.log
```

## Installing Wazuh Agent on Remote Servers

```bash
./common/scripts/install-agent.sh \
    /path/to/ssh-wrapper \
    server-name \
    10.10.195.10 \
    registration-password
```

## ModSecurity Modes

| Mode | Config | Behavior |
|------|--------|----------|
| Detection Only | `SecRuleEngine DetectionOnly` | Logs threats, does NOT block (default) |
| Enforcement | `SecRuleEngine On` | Logs AND blocks — enable after tuning |

**Recommended rollout:**
1. Deploy in `DetectionOnly` mode (default)
2. Monitor /var/log/modsec_audit.log for 1-2 weeks
3. Add CRS exclusions for false positives
4. Switch to `SecRuleEngine On` in tenant modsecurity config

## Rollback

```bash
# Stop container (nginx on host resumes if installed)
docker stop security-bundle

# Remove container but keep data volumes
docker rm security-bundle

# Data is preserved in Docker volumes:
#   security-bundle-wazuh   - Wazuh agent data
#   security-bundle-indexer - OpenSearch indices
```

## Log Locations

| Component | Path | Purpose |
|-----------|------|---------|
| ModSecurity | /var/log/modsec_audit.log | WAF detections/blocks |
| Nginx access | /var/log/nginx/access.log | All HTTP requests |
| Nginx error | /var/log/nginx/error.log | Errors + ModSecurity warnings |
| CrowdSec | docker exec: `cscli alerts list` | Detected scenarios |
| Fail2Ban | /var/log/fail2ban.log | Ban/unban actions |
| Wazuh alerts | /var/ossec/logs/alerts/alerts.json | SIEM alerts |
| Watchdog | /var/log/security-bundle/watchdog.log | Health checks |
| Log Shipper | /var/log/security-bundle/log-shipper.log | MySQL shipping |
| supervisord | /var/log/security-bundle/supervisord.log | Process manager |

## MySQL Tables

All stored in the database specified in tenant.conf:

- **wazuh_alerts** — All Wazuh SIEM alerts (rule ID, level, source IP, raw JSON)
- **watchdog_events** — Backend health events (failover, failback, drain)
- **ban_events** — Fail2Ban + CrowdSec ban/unban events
- **nginx_hourly_stats** — Aggregated nginx traffic stats

All tables are partitioned by month for easy data lifecycle management.
