# 3-node sample — `btcl-mysql`

A minimal real-deploy template for the current scope:

> One MySQL investigator only — the client-vantage probe. Three failures in a
> row → the FailoverCoordinator state machine prints
> `Fencing master.` then `Promoting slave (the-slave) to master.`

Three Debian 12 hosts on the same L2:

| Host    | IP             | Role                | nw-agent does …                       |
|---------|----------------|---------------------|----------------------------------------|
| `mysql-master` | 10.10.199.41 | MySQL master   | Passive — etcd peer + lifecycle SM only. |
| `mysql-slave`  | 10.10.199.42 | MySQL slave    | Passive — etcd peer + lifecycle SM only. |
| `app-client`   | 10.10.199.43 | App / JDBC client | Active — runs `mysql.remote.client` probe against the master, drives the FailoverCoordinator on 3-strike SDOWN. |

The same nw-agent binary runs on every host. Behavior differs only because
of which facets are set in each host's env.

## Files

| File                          | Used on               | Purpose |
|-------------------------------|-----------------------|---------|
| `etcd-mysql-master.env`       | `mysql-master`        | etcd peer flags |
| `etcd-mysql-slave.env`        | `mysql-slave`         | etcd peer flags |
| `etcd-app-client.env`         | `app-client`          | etcd peer flags |
| `nw-agent-mysql-master.env`   | `mysql-master`        | agent identity, no facets |
| `nw-agent-mysql-slave.env`    | `mysql-slave`         | agent identity, no facets |
| `nw-agent-app-client.env`     | `app-client`          | agent identity + `mysql-client-here` facet + MySQL probe config |
| `supervisord.conf`            | every host            | brings up etcd + nw-agent |

## Boot sequence

1. Distribute the etcd binary, the `nw-agent` JAR (or native binary), the
   env files for *this host*, and `supervisord.conf` into `/etc/nw/` and
   `/var/lib/nw/` on each box.

2. On each host: `supervisord -c /etc/nw/supervisord.conf`.

3. Wait ~10 s. All three etcd peers form the cluster; all three nw-agents
   reach `ALIVE` (visible in `/agent/info`'s `lifecycle_phase`).

4. Smoke-verify:

   ```bash
   # cluster picture
   etcdctl --endpoints=http://10.10.199.41:2379 endpoint status --write-out=table
   etcdctl --endpoints=http://10.10.199.41:2379 get /agents/ --prefix --keys-only

   # observations — only mysql-master entries should appear, all published
   # by app-client (the sole observer)
   etcdctl --endpoints=http://10.10.199.41:2379 \
       get /clusters/btcl-mysql/observations/ --prefix

   # each agent's view
   curl -s http://10.10.199.41:7102/agent/info | jq
   curl -s http://10.10.199.43:7102/agent/info | jq
   ```

5. Simulate master failure:

   ```bash
   ssh mysql-master "sudo systemctl stop mysql"
   ```

   After three failed ticks (~15 s), the `app-client` console prints:

   ```
   Fencing master (mysql-master).
   Promoting slave (the-slave) to master.
   ```

   `/agent/info` on `app-client` shows `failover_phase: "DONE"` and
   `last_failover_trigger: {target: "mysql-master", investigator: "mysql.remote.client", consecutive_failures: 3}`.

## What this sample DOES exercise

- Multi-peer etcd cluster: every agent talks to its local peer with
  cross-host failover via the jetcd multi-endpoint client.
- `AgentLifecycleMachine` state machine on every host (`STARTING` →
  `CONNECTING_FABRIC` → `DETECTING_FACETS` → `ARMING_INVESTIGATORS` → `ALIVE`).
- One probe (`mysql.remote.client`) running every 5 s on `app-client` only.
- `ObservationCache` watch-based mirror on every host — passive nodes see the
  same evidence as the active observer.
- `FailureTracker` counting consecutive DEAD verdicts; threshold 3.
- `FailoverCoordinator` state machine (`WATCHING` → `FENCING_MASTER` →
  `PROMOTING_SLAVE` → `DONE`).

## What this sample does NOT exercise yet

- Coordinator election (FabricLock stubbed). All 3 agents observe the same
  evidence but only `app-client` produces it, so only `app-client`'s SM
  fires — the demo is naturally single-actor.
- Real fence + promote: the SM prints two lines and synthesizes the
  completion events. The real Dispatcher + ActionEndpoint land next.
- Permission matrix — not loaded yet.

When the next milestone lands (Coordinator election + Dispatcher + real
Actions + Permissions), this same file set extends in place — only the
agent binary and one new YAML (`permission-matrix.yml`) need to change.
