# Night-Watcher Documentation

Unified operations stack for Telcobright Routesphere: security monitoring, HA controller, identity management, API gateway, and dashboard.

## Documentation Index

### HA Controller (`docs/ha-controller/`)
- [architecture.md](ha-controller/architecture.md) — Quorum-based sentinel consensus, failover coordinator, resource types, Consul KV schema, Go package map, full config reference

### Security Bundle (`docs/security-bundle/`)
- [architecture.md](security-bundle/architecture.md) — Nginx+WAF, Wazuh SIEM, CrowdSec, Fail2Ban, watchdog, log-shipper, supervisord process management, tenant config, entrypoint init sequence

### Dashboard (`docs/dashboard/`)
- [architecture.md](dashboard/architecture.md) — React 18 + MUI SPA, all pages, auth system, API dependencies, build instructions

### Identity Management (`docs/identity/`)
- [architecture.md](identity/architecture.md) — Keycloak backend, custom React UI, JWT flow, REST APIs, role-based access, deployment plan
- [keycloak-identity-design.md](identity/keycloak-identity-design.md) — Detailed Keycloak integration design, file layout, MySQL schema
- [2fa-web-design.md](identity/2fa-web-design.md) — Nginx-enforced 2FA for web apps (TOTP + email OTP)
- [2fa-ssh-design.md](identity/2fa-ssh-design.md) — Google Authenticator PAM for SSH access

### API Gateway (`docs/api-gateway/`)
- [architecture.md](api-gateway/architecture.md) — Spring Cloud Gateway integration, 8 RBAC policies, data-level authorization, audit logging, Keycloak connection

### LXC Containers (`docs/lxc-containers/`)
- [overview.md](lxc-containers/overview.md) — 3-container architecture, IP allocation, communication flow, image build convention, lifecycle commands, local dev setup
- [night-watcher.md](lxc-containers/night-watcher.md) — 11-process supervisord container, tenant configs, multi-node HA deployment
- [keycloak.md](lxc-containers/keycloak.md) — Keycloak 24 + Java 21, realm setup, systemd service, MySQL schema
- [api-gateway.md](lxc-containers/api-gateway.md) — Spring Cloud Gateway JAR, policy architecture, auth flow, rebuild process

### Deployment (`docs/deployment/`)
- [architecture.md](deployment/architecture.md) — LXC container deployment, multi-tenant multi-node setup, networking (IP conventions, WireGuard), production servers, sigtran HA status

## Quick Start for New Agents

1. Read `CLAUDE.md` in project root for guidelines and port conventions
2. Read the relevant `docs/<feature>/architecture.md` for the area you're working on
3. Read `docs/lxc-containers/overview.md` to understand the 3-container deployment model
4. For ha-controller Go code: `cd ha-controller && make test && make build`
5. For dashboard React code: `cd dashboard && npm run build`
6. For API gateway Java code: `cd ../api-gateway && mvn clean package -DskipTests`
7. Tenant configs are in `tenants/<tenant>/` — never hardcode IPs, read from config
8. SSH to servers via: `routesphere-core/tools/ssh-automation/servers/<tenant>/ssh <server> "cmd"`
9. Shared instruction for cross-agent work: `/tmp/shared-instruction/api-gateway-keycloak-integration.md`

## Port Reference

| Port | Service | Container |
|------|---------|-----------|
| 80/443 | Nginx (HTTP/HTTPS) | night-watcher |
| 1514/1515 | Wazuh agent | night-watcher |
| 5601 | Wazuh Dashboard | night-watcher |
| 7100 | Dashboard (Vite) | night-watcher |
| 7101 | Module status API | night-watcher |
| 7102 | hactl status API | night-watcher |
| 7104 | Keycloak | keycloak |
| 8001 | API Gateway | api-gateway |
| 9200 | OpenSearch | night-watcher |

## Credentials (dev/test only)

| Service | Username | Password |
|---------|----------|----------|
| Keycloak admin console | admin | admin |
| Night-watcher realm user | admin | Admin1234 |
| Night-watcher realm user | operator | Operator1234 |
| MySQL (local) | root | 123456 |
