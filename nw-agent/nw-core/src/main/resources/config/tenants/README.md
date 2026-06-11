# nw-agent — tenant-profile config layout

Mirrors the routesphere-core convention:

```
src/main/resources/
├── application.properties                 ← entry point (active tenant + profile)
└── config/tenants/
    ├── <tenant>/<profile>/profile-<profile>.yml
    ├── …
    └── example/                           ← documentation template (every key annotated)
        └── dev/profile-dev.yml
```

## Selecting the active tenant + profile

`application.properties` declares two properties:

```properties
nw.tenant.name=btcl
nw.tenant.profile=dev
```

At startup `TenantProfileConfigSource` reads those, loads `config/tenants/<name>/<profile>/profile-<profile>.yml` from the classpath, and registers it as a SmallRye config source.

Override at deploy / dev time with env vars (Quarkus auto-converts the dotted keys):

```bash
NW_TENANT_NAME=bdcom NW_TENANT_PROFILE=prod \
  java -jar quarkus-run.jar
```

## Tenants currently scaffolded

| Tenant   | dev | prod | staging | Notes |
|----------|-----|------|---------|-------|
| `btcl`   | ✓   | ✓    | ✓       | dev profile is fully wired against the BTCL etcd cluster + dell-sms-master MySQL — works as-is |
| `bdcom`  | ✓   | ✓    | ✓       | placeholders — fill TODO values when night-watcher deploys to bdcom |
| `link3`  | ✓   | ✓    | ✓       | placeholders |
| `example`| ✓   | ✓    | ✓       | documentation template — every key annotated. Copy it when adding a new tenant. |

## Per-profile semantics

| Profile   | When to use | Distinguishing knobs |
|-----------|-------------|----------------------|
| `dev`     | Developer running locally against test infra; verbose logging | `static-facets` set so probes fire; DEBUG logging on `com.tb.nw` |
| `prod`    | Production deployment on tenant hosts | Tighter thresholds, INFO logging, `node-name` required (no default) |
| `staging` | Pre-production rehearsal | Same shape as prod with a distinct cluster-name suffix |

These three are the canonical set across every tenant. **Tenant-specific** profiles (e.g. routesphere has a `bdcom/remote-debug` and `ccl/mock`) are added per-tenant when a particular need arises — not baked into every tenant up front.

## Override precedence

SmallRye Config order (lowest → highest):

1. Defaults baked into SPI / nw-core classes.
2. `application.properties` (framework defaults + tenant selector).
3. The profile YAML (loaded by `TenantProfileConfigSource`, ordinal 250).
4. System properties (`-Dnw.agent.cluster-name=...`).
5. Env vars (`NW_AGENT_CLUSTER_NAME=...`).

So the tenant deployment YAML provides the baseline; the deploy script's env vars provide host-identity (`NW_NODE_NAME`) and secrets (`NW_MYSQL_PASSWORD`) that never live in the YAML.

## Adding a new tenant

```bash
cp -r config/tenants/example config/tenants/<new-tenant>
# Edit the three profile YAMLs:
sed -i 's/example-/<new-tenant>-/g' config/tenants/<new-tenant>/*/profile-*.yml
# Then fill in the real endpoints / hosts / thresholds.
```

Add an entry under `tenants[…]` in `application.properties` for operator visibility (the array is currently informational — the active tenant is selected by `nw.tenant.name`).

## Adding a one-off profile (tenant-specific)

If a particular tenant needs an extra profile (e.g. `remote-debug` for attaching from a laptop, or `mock` for unit-test-only runs), drop a new subdirectory next to `dev/prod/staging`:

```
config/tenants/bdcom/
├── dev/
├── prod/
├── staging/
└── remote-debug/                          ← tenant-specific, not part of the canonical 3
    └── profile-remote-debug.yml
```

Switch to it the same way:

```bash
NW_TENANT_NAME=bdcom NW_TENANT_PROFILE=remote-debug java -jar quarkus-run.jar
```

## Security: HTTP bind policy

The agent's status API (port 7102) **never** binds to `0.0.0.0`. Default
is `127.0.0.1` — reachable only from the host itself. Production /
staging deploys override this to the host's overlay (WireGuard) IP via
the `NW_BIND_HOST` env var so the dashboard + operators on the overlay
can reach `/agent/info`, but the port is **not** exposed on the host's
LAN or public interfaces.

```bash
# prod / staging — bind to the host's overlay IP
NW_TENANT_NAME=btcl NW_TENANT_PROFILE=prod \
NW_BIND_HOST=10.10.196.20 \
NW_NODE_NAME=mysql-master-1 NW_MYSQL_PASSWORD=… \
  java -jar quarkus-run.jar

# dev — leave NW_BIND_HOST unset; falls back to 127.0.0.1
```

If `NW_BIND_HOST` is omitted in any profile, the agent falls back to
`127.0.0.1` — **fail-secure**. The deploy is expected to verify
`/agent/info` is reachable over the overlay (and **not** from the LAN)
after rollout.

When the ActionEndpoint gRPC port (7103) lands it follows the same policy.

## Secrets

**Never commit credentials.** The profile YAMLs reference these via env:

| Env var               | Used by                                                  |
|-----------------------|----------------------------------------------------------|
| `NW_MYSQL_PASSWORD`   | `MysqlConfig.password` (Optional — agent boots without)  |
| `NW_BIND_HOST`        | `quarkus.http.host` (defaults to `127.0.0.1` — see bind policy above) |
| Future: `NW_FABRIC_CLIENT_KEY` / `NW_FABRIC_TRUSTED_CA` | etcd mTLS when it lands  |
| Future: `NW_SPIFFE_SOCKET`                              | SPIRE workload API when identity lands |
