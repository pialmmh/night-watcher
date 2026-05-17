# nw-agent

Night-Watcher agent — Quarkus + native-image, bundled with etcd.

## Layout

```
nw-agent/
├── nw-spi/          plugin contract interfaces (no Quarkus dep)
├── nw-fabric-api/   Fabric* interfaces (no Quarkus dep)
├── nw-fabric-etcd/  jetcd-backed Fabric implementation
├── nw-core/         Quarkus agent (bootstrap, activation, observation, resolver, dispatcher)
├── plugins/
│   └── nw-mysql/    built-in MySQL plugin
└── nw-dist/         assembly module — produces the runnable artifact
```

## Build (JVM, dev iterate)

```bash
mvn -DskipTests package
```

## Run dev mode

```bash
cd nw-dist
mvn quarkus:dev
# → http://127.0.0.1:7102/agent/info
# → http://127.0.0.1:7102/q/health
```

## Build native (CI / shipped binary)

```bash
mvn -DskipTests package -Dnative -Dquarkus.native.container-build=true
# → nw-dist/target/nw-dist-1.0.0-SNAPSHOT-runner   (native ELF, ~80 MB)
```

Requires Docker or Podman locally; Mandrel is pulled as a container image.

## What's wired up so far

- Multi-module Maven layout compiles and packages.
- `EtcdFabric` (jetcd-backed) implements KV / Lease / Txn. Lock = stub.
- Agent boots in ~900 ms (JVM mode), connects to local etcd.
- `AgentLease` owns the shared lease; per-agent keys (`heartbeat`, `facets`) and per-cluster keys (`observations/...`) all tied to it. Clean shutdown revokes; SIGKILL leaves TTL to clean up.
- `StaticFacetDetector` reads facets from `nw.agent.static-facets`. Plugin-supplied detectors plug in via CDI.
- `InvestigatorRunner` ticks every 5 s, runs every CDI-registered `HealthCheck`, publishes typed `Observation` JSON to `/clusters/{cluster}/observations/{target}/{publisher}/{investigator}`.
- `HostUptimeHealthCheck` (built-in) reads `/proc/uptime`; FAST when uptime ≥ 60 s, DEGRADED otherwise.
- `ClusterType` enum (MySqlMasterSlave, MySqlGalera, PostgresStreaming, Nginx, SmsGateway, SigtranSgw, FreeSwitch, Kafka, RedisSentinel, Generic, …) — typed classifier propagated into every observation.
- `/agent/info` returns live status: node, cluster, cluster_type, fabric reachability, last heartbeat, facets, recent observations.
- **`nw-mysql` plugin live**: `MysqlFacetDetector` (detects master/slave by `@@read_only` + `SHOW REPLICA STATUS`); `MySqlLocalHealthCheckerMaster` (canary query + replica count + uptime); `MySqlLocalHealthCheckerSlave` (replica thread state + lag); `MySqlRemoteCheckerMaster` (cross-host latency). MySQL 5.7 and 8.0+ both supported via syntax fallback.
- `HealthCheck.requiredFacets()` — minimum-viable activation: probes self-declare prerequisites and the runner skips them when the facet set isn't satisfied.

## Smoke test against local etcd + local MySQL

```bash
# requires: etcd on 127.0.0.1:2379, MySQL on 127.0.0.1:3306

cd nw-dist/target/quarkus-app
NW_NODE_NAME=smoke-test \
NW_AGENT_CLUSTER_NAME=local-mysql \
NW_AGENT_CLUSTER_TYPE=MySqlMasterSlave \
NW_MYSQL_LOCAL_HOST=127.0.0.1 \
NW_MYSQL_PORT=3306 \
NW_MYSQL_USERNAME=root \
NW_MYSQL_PASSWORD=123456 \
java -jar quarkus-run.jar &

# in another shell:
curl -s http://127.0.0.1:7102/agent/info | jq
etcdctl get /agents/ --prefix
etcdctl get /clusters/btcl-platform-mysql/observations/ --prefix
```

Expect:
- `/agents/{node}/heartbeat` + `/agents/{node}/facets` refresh every 5 s / 30 s.
- `/clusters/{cluster}/observations/{node}/{node}/host.uptime` carries a typed JSON Observation.
- Killing the agent gracefully revokes the lease; all keys disappear immediately.

## What's next

- Observation pipeline (typed records, publisher, watch-based view).
- First HealthCheck implementation in `nw-mysql` (`MySqlLocalHealthCheckerMaster`).
- Activation engine (catalog + predicates + scheduler).
- State-machine integration for multi-step actions.
- Deploy tool (`deploy/deploy.sh` + components/) following the orchestrix-v2 pattern.

See the Trilium `Night-Watcher → Design → Story` and `Technical` subtrees for the full design.
