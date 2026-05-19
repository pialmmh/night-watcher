# Night-Watcher

Operations stack for Telcobright Routesphere — HA control plane + security
monitoring + identity. The HA control plane was rewritten end of 2026 Q2;
the deployment is mid-migration from "one LXC container per host with
supervisord" to docker-compose with one service per container.

## What's in this repo (current state)

| Component | Location | Language | Purpose |
|---|---|---|---|
| **nw-agent** | `nw-agent/` | Java 21 / Quarkus | HA control plane: per-node agent + bundled etcd. Replaces the old Go `hactl`. See `nw-agent/CLAUDE.md` for the agent's own design notes. |
| **Security bundle (transitional)** | `Dockerfile`, `supervisord.conf`, `entrypoint.sh`, `common/`, `tenants/<t>/` | Bash, Python | Nginx+ModSec, Wazuh SIEM, CrowdSec, Fail2Ban, watchdog. Still one all-in-one container today; being split into per-service containers — see `docs/docker-compose-migration.md`. |
| **Dashboard** | `dashboard/` | React 18 + Vite + MUI | Security dashboard. JWT auth via Keycloak. |
| **Identity** | `dashboard/src/auth/` + Keycloak | React, Java | Login, profile, user management. |
| **Tenant configs** | `tenants/<tenant>/` | YAML, conf | Per-tenant nginx / wazuh / crowdsec / fail2ban / modsecurity overlays + `tenant.conf`. |
| **Sample 3-node deploy** | `nw-agent/configs/sample-3node/` | env files, supervisord conf | Reference layout for running nw-agent + bundled etcd across 3 hosts. |
| **Docs** | `docs/` | Markdown | Architecture, migration plan, identity / 2FA designs. |

## What got removed (Q2 2026 cleanup)

- `ha-controller/` Go module — replaced in full by `nw-agent/`.
- `launch.sh`, `deploy-bdcom.sh` — LXC-coupled deploy scripts.
- `tenants/*/ha-controller.yml`, `tenants/*/nodes.json` — old hactl schema.
- `common/scripts/ha-cluster-api.py` — old v1 API.
- `dashboard/mock-hactl.py` — old mock for the hactl API.
- `docs/lxc-containers/`, `docs/ha-controller/` — v1 docs.
- All Consul references in the bundle Dockerfile / supervisord / entrypoint.

`docs/security-bundle/architecture.md` and `docs/deployment/architecture.md`
have a deprecation banner — they still describe the LXC era and are kept
as historical reference until the compose migration completes.

## Build & run

```bash
# Build the (transitional) security-bundle image — still all-in-one.
./build.sh latest

# Deploy to a tenant (Docker-based, runs `docker run` on the remote).
./deploy.sh btclsms

# Build the new HA control plane (separate artifact).
cd nw-agent && mvn -DskipTests clean package
```

The compose entry point is `docker-compose.yml` at the repo root — see the
migration doc for status of each service. Today only `nw-agent` and
`security-bundle` are declared; the rest are commented placeholders.

## Project layout

```
night-watcher/
├── Dockerfile              # security-bundle image (Wazuh + nginx + crowdsec + fail2ban)
├── build.sh                # docker build wrapper
├── deploy.sh               # docker-based remote deploy (per tenant)
├── supervisord.conf        # process manager inside security-bundle (9 entries)
├── entrypoint.sh           # container init: wires tenant configs
├── docker-compose.yml      # NEW: compose entry point (skeleton)
│
├── nw-agent/               # Java HA control plane (Quarkus + bundled etcd)
│   ├── nw-spi/             # plugin SPI (HealthCheck, FailoverAction, …)
│   ├── nw-fabric-api/      # Fabric interfaces (KV / lease / lock / txn)
│   ├── nw-fabric-etcd/     # jetcd-backed Fabric impl
│   ├── nw-core/            # the agent — lifecycle SM, coord, observation cache
│   │   ├── sm/             # minimal StateMap DSL (routesphere style)
│   │   ├── lifecycle/      # AgentLifecycleMachine
│   │   └── coord/          # FailoverCoordinator + FailureTracker
│   ├── plugins/nw-mysql/   # MySQL plugin
│   │   ├── probe/          # observe side: MySqlRemoteClient + 4 dormant probes
│   │   └── orchestrator/   # act side: placeholder for FailoverAction impls
│   ├── nw-dist/            # assembly module — produces the runner JAR
│   └── configs/sample-3node/  # role-split env files for a 3-host deploy
│
├── dashboard/              # React + Vite + MUI
│   └── src/auth/, pages/, components/, api/
│
├── common/                 # security-bundle shared configs + scripts
│   ├── nginx/, modsecurity/, wazuh/, mysql/
│   └── scripts/            # watchdog.sh, log-shipper.py, module-status-api.py,
│                             install-agent.sh, wazuh-manager-wrapper.sh
│
├── docs/
│   ├── docker-compose-migration.md  # the migration plan
│   ├── api-gateway/, dashboard/, deployment/, identity/, security-bundle/
│   ├── index.md
│   └── TODO.md
│
└── tenants/                # per-tenant overlays
    ├── btclsms/   # tenant.conf + nginx/, crowdsec/, fail2ban/, modsecurity/, watchdog/, wazuh/
    └── bdcom/     # same shape
```

## Ports

| Port | Service |
|------|---------|
| 80 / 443 | Nginx (HTTP / HTTPS) |
| 1514 / 1515 | Wazuh agent |
| 2379 / 2380 | nw-agent's bundled etcd (client / peer) |
| 5601 | Wazuh Dashboard |
| 7100 | Dashboard dev (Vite) |
| 7101 | module-status-api (Python — legacy; dashboard repoint pending) |
| 7102 | nw-agent status API (`/agent/info`) |
| 7103 | nw-agent ActionEndpoint gRPC (future) |
| 9200 | Wazuh Indexer (OpenSearch) |
| 55000 | Wazuh API |

## Supervisord processes inside security-bundle (priority order)

`nginx(10) → crowdsec(20) → fail2ban(30) → wazuh-indexer(35) → wazuh-manager(40) → wazuh-dashboard(50) → module-status-api(55) → watchdog(60) → log-shipper(70)`

Note: the old `consul(12)` and `hactl(15)` entries were removed. The new HA
control plane (`nw-agent` + bundled etcd) runs as its own compose service.

## HA control plane (nw-agent)

See `nw-agent/CLAUDE.md` for the full design. Short summary:

- One agent per host. Quarkus / Java 21. Bundled etcd as the coordination
  fabric (no Consul).
- Plugin SPI in `nw-spi/`: `HealthCheck`, `FacetDetector`, `FailoverAction`,
  `HealthAggregator`, `CandidateSelector`, `FailoverPlanGenerator`,
  `ClusterCondition`, `StatefulAction`.
- High-level entry classes are written as state machines using a small
  in-repo DSL (`nw-core/sm/`). `AgentLifecycleMachine` describes the agent's
  lifecycle; `FailoverCoordinator` describes the orchestration sequence.
- One active MySQL investigator in the current cut:
  `mysql.remote.client` — runs a configurable canary query (default
  `SHOW DATABASES`) against the master from an app-tier host. Three
  consecutive `DEAD` verdicts trigger the coordinator, which (today)
  prints the demo failover lines to stdout. Real actions land when the
  Dispatcher + ActionEndpoint do.

## Identity (Keycloak)

Keycloak backend with custom React UI; no Keycloak UI exposed to users.

| Page | Route | Access |
|------|-------|--------|
| Login | `/login` | Public |
| Profile | `/profile` | Authenticated |
| User Management | `/users` | `admin` role |

JWT flow: Keycloak issues → sessionStorage → `Authorization: Bearer` on
every API call → auto-refresh → 401 redirects to `/login`.

Keycloak config: realm `night-watcher`, client `nw-dashboard` (public SPA,
direct-access grants), roles `admin / operator / viewer`.

## Routesphere context

Multi-tenant telecom platform (Quarkus 3.x / Java 21) handling SMS, Voice,
SIP routing. Services needing HA:

| Service | Ports | HA status |
|---|---|---|
| sigtran (SS7 MAP) | 8282 UDP | future — will be a sigtran probe + actions in nw-agent |
| routesphere-core | 19999, 18093 | future |
| FreeSWITCH | 5060 SIP | future |

### Production servers (BTCL)

| Tenant | Server | IP |
|---|---|---|
| btcl | dell-sms-master | 10.246.7.102 |
| btcl | dell-sms-slave | 10.246.7.103 |
| btcl | sbc1 | 192.168.24.101 |
| btcl | sbc4 | 192.168.24.104 |
| bdcom | bdcom1 | 10.255.246.173 |
| link3 | link3-1 | (link3 net) |

**As of 2026-05-19**, no night-watcher containers run on any tenant host
— the 3 v1 LXC containers in BTCL were deleted; their config snapshots
are at `/tmp/btcl-nw-v1-snapshots/`. Redeploy uses the new compose flow
once the per-service split is done.

## Guidelines

- Do not git push until asked.
- Java 21 for nw-agent (no Go in this project anymore).
- Dashboard dev port: 7100. Never use the 3000 range.
- MySQL partitions: create all partitions in `CREATE TABLE`, not via `ALTER`.
- For new long-running services / orchestrators, prefer the StateMap DSL
  (see `nw-agent/nw-core/sm/`) so the top of the class reads as named
  business phases.
