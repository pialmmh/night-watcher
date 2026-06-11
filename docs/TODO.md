# Night-Watcher / BTCL Sigtran HA — Resume Checklist

Last updated: 2026-04-17

## Context Snapshot

**Goal:** Implement sigtran HA failover between `dell-sms-master` (10.246.7.102) and `dell-sms-slave` (10.246.7.103) for BTCL tenant, coordinated by 3 night-watcher nodes using quorum consensus.

**Active node must hold:**
- VIP `10.246.7.101/28` on bond0 (only IP STP 10.240.191.101 accepts)
- Sigtran service (SS7/MAP, UDP 8282)
- MySQL master
- Redis

**Quorum nodes planned:** `sbc4` (192.168.24.104), `dell-sms-slave` (10.246.7.103), `sbc1` (192.168.24.101).

## Key Findings (do not re-investigate)

- STP (10.240.191.101) only replies to VIP `.101`, not to physical IPs `.102` / `.103`.
- Dell-sms-slave was routing STP traffic via wrong gateway `.104` (VRRP backup). Fixed runtime with:
  `sudo ip route replace 10.240.191.0/24 via 10.246.7.105 dev bond0`
  — **not persisted yet**; lost on reboot.
- Ping from slave `.103` to STP shows 100% loss. Expected — STP only accepts VIP.
- Master's VIP path to STP is healthy.

## Pending Tasks (resume here)

### Network / Routing
- [ ] Persist STP routes on dell-sms-slave (`10.240.191.0/24` and `10.240.231.0/24` via `10.246.7.105`). Use `/etc/network/interfaces` or netplan — verify with reboot.
- [ ] Quick VIP smoke test on slave: temporarily assign `10.246.7.101/28` to bond0, ping STP from VIP source, remove. Confirms failover path before committing to HA.
- [ ] Decide BTCL sigtran HA IP scheme: `10.246.7.x` vs `10.247.7.x` (still open).

### Sigtran on Slave
- [ ] Install Java 8 on dell-sms-slave.
- [ ] Copy sigtran deployment from master to slave (keep stopped — hactl starts it on failover).

### HA Controller (hactl)
- [ ] Deploy updated sentinel-aware hactl binary to all 3 night-watcher containers.
- [ ] Verify `tenants/btclsms/ha-controller.yml` matches the new sentinel format (fail_threshold, check_interval, scope, action resources).

### Consul
- [ ] Set up 3-node Consul cluster on sbc4, dell-sms-slave, sbc1.
- [ ] Confirm leader election + KV reachability from all 3.

### Redis / MySQL (Phase 2-3)
- [ ] Switch BTCL Redis to Sentinel mode (kafka1/2/3 already run sentinel for bdcom — mirror that).
- [ ] Set up MySQL replication master → slave (Phase 3, after sigtran HA is green).

### Dashboard / API Commit
- [ ] Commit HA cluster CRUD work (uncommitted on main):
  - `common/scripts/ha-cluster-api.py` (Python REST API on :7105)
  - `dashboard/src/pages/HaClusterManager.jsx`
  - `dashboard/src/api/ha-cluster.js`
  - `dashboard/dev-mock-api.js`
  - `dashboard/vite.config.js`, `dashboard/src/App.jsx`, `dashboard/src/components/Layout.jsx`
  - `tenants/mock/` (new mock profile)
  - `tenants/btclsms/ha-controller.yml` (sentinel format)
  - MySQL schema for `ha_cluster`, `ha_node`, `ha_resource_group`, `ha_resource`, `ha_check`, `ha_cluster_status`
- [ ] Do NOT push until user asks.

## Reference

- Sigtran architecture: `/tmp/shared-instruction/sigtran-ha-infrastructure-briefing.md`
- HA controller design: `docs/ha-controller/architecture.md`
- SSH wrappers: `routesphere-core/tools/ssh-automation/servers/btcl/ssh <server>`

## Phases (high level)

1. **Phase 1 — Networking + VIP proof** (current): persist routes, VIP smoke test.
2. **Phase 2 — Sigtran HA**: slave install, hactl deploy, Consul cluster, first managed failover.
3. **Phase 3 — Data HA**: MySQL replication, Redis Sentinel.
4. **Phase 4 — Dashboard / tooling**: commit CRUD, wire real backend to HaClusterManager.
