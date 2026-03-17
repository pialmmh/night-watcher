# HA Controller — Architecture

## Goal

Quorum-based active-standby failover for Telcobright Routesphere services (sigtran, routesphere-core, FreeSWITCH). Runs inside the night-watcher LXC container and SSHes to the host to execute all actions.

## How It Works

```
Every hactl node (5s tick):
  1. Run health checks against the VIP / active node's services
  2. If checks fail N consecutive times → mark SDOWN (subjective down)
  3. Publish observation to Consul KV
  4. Read all peer observations
  5. If quorum nodes agree SDOWN → ODOWN (objective down)

Coordinator (Consul lock holder) on ODOWN:
  6. Select best standby node (by priority, must be self-healthy)
  7. CAS increment failover epoch (prevent double failover)
  8. SSH to OLD host → run deactivate sequence (reverse order)
  9. SSH to NEW host → run activate sequence (forward order)
  10. Update active node in Consul KV
```

## Key Design Decision

**Consul lock holder = failover coordinator, NOT the active node.**

Before: whoever holds the lock runs the VIP (active = lock holder).
After: lock holder just coordinates. Active node tracked separately in `ha-controller/{cluster}/active` KV. All nodes health-check. Coordinator only executes failover when quorum agrees.

## Consul KV Schema

```
ha-controller/{cluster}/leader              → coordinator lock (session-based)
ha-controller/{cluster}/active              → "node-1" (which node is active)
ha-controller/{cluster}/failover-epoch      → "3" (monotonic, CAS prevents double failover)
ha-controller/{cluster}/observations/node-1 → JSON Observation
ha-controller/{cluster}/observations/node-2 → JSON Observation
ha-controller/{cluster}/observations/node-3 → JSON Observation
```

## State Machine

```
INIT → FOLLOWER ←→ LEADER (coordinator) → STOPPING
```

All nodes (FOLLOWER and LEADER) run health checks and publish observations. Only the LEADER (coordinator) executes failovers.

## Anti-Flap

- `max_failovers` (default 3) within `failover_window` (default 1h)
- CAS on failover epoch prevents two coordinators from executing simultaneously
- No automatic failback (prevents ping-pong)

## Resource Types

| Type | File | Purpose |
|------|------|---------|
| `vip` | `internal/resource/vip.go` | `ip addr add/del` + gratuitous ARP |
| `action` | `internal/resource/action.go` | Generic: configurable activate/deactivate/check shell commands |
| `noop` | `internal/resource/noop.go` | Testing dummy |

## Failover Sequence Example (sigtran)

On failover from node-A to node-B:

**Deactivate on A (reverse order):**
1. `curl -X POST .../api/ha/demoted` (notify routesphere)
2. `lxc exec sigtran -- /opt/sigtran/stop.sh` (stop service)
3. `ip addr del 10.246.7.101/24 dev lxdbr0` (remove VIP)

**Activate on B (forward order):**
1. `ip addr add 10.246.7.101/24 dev lxdbr0` + arping (assign VIP)
2. `lxc exec sigtran -- /opt/sigtran/start.sh` (start service)
3. `curl -X POST .../api/ha/promoted` (notify routesphere)

## API Endpoints (port 7102)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/status` | Full state: activeNode, sdown, odown, observations, groups |
| POST | `/failover` | Manual failover: `{"targetNode": "node2"}` (coordinator only) |

## Go Package Map

```
cmd/hactl/main.go           → CLI entry, wires everything
internal/api/server.go       → HTTP status + manual failover API
internal/config/config.go    → YAML config + validation + defaults
internal/consul/client.go    → Consul KV: Get, Set, CAS, ListPrefix, Delete
internal/consul/leader.go    → Session-based leader election
internal/engine/engine.go    → Sentinel-aware reconciliation loop
internal/executor/executor.go → Executor interface
internal/executor/local.go   → Shell via /bin/sh
internal/executor/ssh.go     → SSH remote execution
internal/healthcheck/check.go → Ping, TCP, HTTP, Script probes
internal/node/node.go        → Node abstraction + fence
internal/resource/resource.go → Resource interface + state/health enums
internal/resource/vip.go     → VIP: ip addr add/del + arping
internal/resource/action.go  → Generic: configurable shell commands
internal/resource/group.go   → Ordered activate (FIFO) / deactivate (LIFO) with rollback
internal/sentinel/sentinel.go → SDOWN/ODOWN detection, observation publish/read
internal/sentinel/failover.go → Failover coordinator, candidate selection, epoch CAS
```

## Config Structure

```yaml
cluster:
  name: btcl-ha
  tenant: btcl
  quorum: 2                  # nodes needed for ODOWN
  fail_threshold: 3          # consecutive failures → SDOWN
  check_interval: 5s         # reconciliation tick
  auto_failback: false       # never auto-failback
  max_failovers: 3           # anti-flap: max failovers in window
  failover_window: 1h        # anti-flap window
  observation_stale: 30s     # ignore observations older than this

consul:
  address: "127.0.0.1:8500"
  session_ttl: 15s
  lock_delay: 5s

nodes:
  - id: btcl-nw-1
    address: 10.10.189.200   # night-watcher container IP (SSH target)
    priority: 1              # lower = higher priority
    ssh:
      user: root
      key_path: /root/.ssh/id_rsa
    fence_cmd: "ssh root@... 'poweroff'"

groups:
  - id: sigtran-failover
    resources:
      - id: assign-vip
        type: vip
        attrs: { ip: "10.246.7.101", cidr: "24", interface: lxdbr0 }
      - id: manage-sigtran
        type: action
        attrs:
          activate: "lxc start sigtran-btcl"
          deactivate: "lxc stop sigtran-btcl --force"
          check: "lxc exec sigtran-btcl -- curl -sf http://127.0.0.1:8282/pinginfo"
          timeout: "30s"
    checks:
      - name: sigtran-health
        type: http
        target: "http://10.246.7.101:8282/pinginfo"
        expect: '"sctpUp":true'
        scope: cluster       # all nodes check this (for SDOWN/ODOWN)
      - name: local-readiness
        type: script
        target: "lxc exec sigtran-btcl -- curl -sf http://127.0.0.1:8282/pinginfo"
        expect: "sctpUp"
        scope: self           # each node checks own readiness (for candidate selection)
```

## Tests

65 unit tests across config, resource, sentinel, executor packages. Run: `cd ha-controller && make test`
