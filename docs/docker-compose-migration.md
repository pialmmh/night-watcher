# LXC → docker-compose migration

## Where we're coming from

For ~3 months the night-watcher security bundle was deployed as a single
LXC container per host, with **supervisord** inside running 11 processes
(nginx, hactl, Consul, CrowdSec, Fail2Ban, Wazuh Manager / Indexer /
Dashboard, module-status-api, watchdog, log-shipper). The image was built
from this repo's `Dockerfile` + `supervisord.conf` and then launched as an
LXC container via `launch.sh` / `deploy-bdcom.sh` (now deleted).

That model had two problems we're fixing:

1. **Process supervisor inside a container** — supervisord is fine, but it
   means container restart is all-or-nothing for 11 services, and per-service
   resource limits / metrics / image upgrades are awkward.
2. **LXC vs Docker** — the repo's `Dockerfile` produced a Docker image, but
   deployment wrapped that image inside an LXC container on the host. Two
   container abstractions for no real reason; the LXC layer is going away.

## Where we're going

`docker-compose.yml` at the repo root. One service per container. Each
tenant has its own `tenant.env` + overlay configs under `tenants/<tenant>/`.

```
host
├── /etc/night-watcher/
│   ├── docker-compose.yml          (the file in this repo)
│   ├── docker-compose.override.yml (per-host port maps, etc.)
│   └── tenant.env                  (rendered from tenants/<tenant>/tenant.conf)
└── /var/lib/night-watcher/         (volume mounts)
    ├── etcd-data/
    ├── wazuh-manager-data/
    ├── wazuh-indexer-data/
    └── nginx-ssl/
```

## Migration order (gradual)

### Done

- **Remove hactl + Consul from the bundle** — both replaced by `nw-agent/`
  (Quarkus + bundled etcd). `Dockerfile` stage 2 deleted, `supervisord.conf`
  entries deleted, `entrypoint.sh` hactl wiring deleted, all
  `tenants/*/ha-controller.yml` + `nodes.json` removed, `ha-controller/` Go
  module deleted.
- **Skeleton compose file** — `docker-compose.yml` at the repo root, with
  the `nw-agent` and (transitional) `security-bundle` services declared.

### Next — per-service split out of `security-bundle`

Order chosen for blast-radius: smallest-first.

| Order | Service | Source today | Migration step | Open questions |
|---|---|---|---|---|
| 1 | **nginx + ModSecurity** | `[program:nginx]` in supervisord + `common/nginx/`, `common/modsecurity/` | Use `owasp/modsecurity-crs:nginx` upstream image; mount `tenants/<t>/nginx/` + `tenants/<t>/modsecurity/` overlays | SSL cert location and renewal flow |
| 2 | **CrowdSec** | `[program:crowdsec]` + `common/scripts/`, `tenants/<t>/crowdsec/` | Use `crowdsecurity/crowdsec` upstream; bind-mount tenant collections | Bouncer integration with nginx in a separate container |
| 3 | **Fail2Ban** | `[program:fail2ban]` + `tenants/<t>/fail2ban/` | Use `crazymax/fail2ban`; share `/var/log` mount with nginx so it can read access logs | Cross-container log access — likely a named volume or a unix-socket forwarder |
| 4 | **Wazuh Manager / Indexer / Dashboard** | three `[program:wazuh-*]` entries | Use the three `wazuh/wazuh-*:4.7.0` upstream images; named volumes for state | OpenSearch needs `vm.max_map_count=262144` on host — already in `entrypoint.sh`, but compose can't set host sysctls; document as host prereq |
| 5 | **module-status-api** + **watchdog.sh** | `common/scripts/` Python + bash | Containerize as a small Python image. **Or** retire once the dashboard repoints to `nw-agent`'s `/agent/info` and `nw-agent` absorbs the nginx-failover role | Dashboard coupling — see below |
| 6 | **dashboard (React)** | `dashboard/` | Already has its own `Dockerfile` for the React/Vite build. Slot into compose with a port map. | Repoint `dashboard/src/api/ha-cluster.js` from the old hactl REST API to `nw-agent`'s `/agent/info` |
| 7 | **Decommission `security-bundle` container** | supervisord goes away with this | Delete `Dockerfile`, `entrypoint.sh`, `supervisord.conf`, `build.sh` once every service above has its own block | None |

### After

- `deploy.sh` either deletes or becomes a thin wrapper around
  `docker compose --env-file tenants/<t>/tenant.env up -d`.
- `nw-agent`'s `configs/sample-3node/supervisord.conf` (the new
  per-role file) gets replaced by a compose override file that adds
  `nw-agent` + etcd peer addresses per host.
- Tenant configs (`tenants/<tenant>/`) keep their per-service overlays —
  the migration doesn't move those; it just changes which container reads
  them.

## What's intentionally NOT changing yet

- **`common/scripts/watchdog.sh`** — keeps watching nginx for now.
  `nw-agent` will absorb this role once a generic `nginx.client` probe +
  action lands, but that's a follow-up — out of scope of just-getting-off-LXC.
- **`dashboard/src/api/ha-cluster.js`** — still calls the old hactl REST API
  shape. Compose works without touching it; the dashboard will need a
  separate change to point at `/agent/info`.
- **`common/scripts/module-status-api.py`** — keeps serving `:7101` for the
  dashboard until the repoint above is done.

## How to validate at each step

1. Build the image of the service being split out.
2. Bring it up in compose alongside the still-running `security-bundle`.
3. Confirm same surface (same port, same JSON, same logs).
4. Remove the corresponding `[program:…]` entry from `supervisord.conf`,
   rebuild `security-bundle`, redeploy.
5. Old supervisord-managed process disappears; new container takes over
   with zero observed change.

## Status banner for legacy docs

The following docs describe the LXC-era architecture and are stale:

- `docs/security-bundle/architecture.md`
- `docs/deployment/architecture.md`
- (formerly `docs/lxc-containers/` — removed)
- (formerly `docs/ha-controller/` — removed; replaced by `nw-agent/`)

Each has a deprecation banner at the top. They stay around as historical
reference until the compose migration is complete and the new shape is
documented.
