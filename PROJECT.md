# Telcobright High Availability Cluster

## Problem

Our sigtran SS7 stack (and other services) must present a fixed IP to remote peers (MNO MSCs, HLRs, SGWs). Today each service runs on a single VM — if it fails, traffic stops until manual intervention.

We need active/passive HA so that:
- Remote peers always connect to the same IP (floating VIP)
- Only one node is active at a time (no duplicate traffic)
- Failover is automatic, fast (seconds), and split-brain-free
- Custom health probes determine node health (M3UA ASP status, Redis, Kafka, REST, SIP, SSH)
- The same framework covers sigtran and any future service (routesphere, SIP proxies, etc.)

## Requirements

1. **Floating VIP**: A virtual IP that moves to the active node. Remote peers see a single stable IP.
2. **Active/passive**: 2 or 3 identical VMs per cluster. Only the active node holds the VIP and runs the service.
3. **Quorum-based election**: Majority vote prevents split-brain. No manual tie-breaking.
4. **Fencing (STONITH)**: If a node is unreachable but not confirmed dead, fence it (power off / network isolate) before promoting another — prevents two nodes claiming the same IP.
5. **Custom health checks**: Pluggable probes that run periodically on the active node. Failure triggers failover. Probes include:
   - Process liveness (is sigtran JVM running?)
   - Application-level (M3UA ASP UP in logs, SCTP associations established)
   - Downstream dependencies (Redis sentinel reachable, Kafka brokers reachable)
   - Network probes (SSH to peer, REST API health endpoint, SIP OPTIONS ping)
6. **Ordered resource groups**: On failover, actions execute in sequence: assign VIP, send gratuitous ARP, start service, run post-start validation.
7. **Reusable across services**: Same cluster framework for sigtran, routesphere, SIP, etc. — different resource agents per service.
8. **Minimal downtime**: Failover target < 15 seconds for VIP migration + service start.

## Recommended Stack: Corosync + Pacemaker

### Why

Corosync + Pacemaker is the standard Linux HA stack, proven in production telecom deployments worldwide. It is purpose-built for active/passive clustering with VIP failover and is the most mature, widely-deployed solution for this exact problem.

### Components

| Component | Role | Details |
|-----------|------|---------|
| **Corosync** | Cluster membership & messaging | Provides heartbeat, quorum engine, and secure node-to-node communication via UDP/IP multicast or unicast. Detects node failures within seconds. |
| **Pacemaker** | Resource manager | Decides which node runs which resources. Handles failover orchestration, resource ordering, colocation constraints, and fencing. |
| **STONITH / Fencing** | Split-brain prevention | Ensures a failed/partitioned node is powered off or isolated before another node takes over. Prevents two nodes holding the same VIP. Options: IPMI, libvirt (for VMs), SSH-based, cloud APIs. |
| **OCF Resource Agents** | Service management | Shell scripts that Pacemaker calls to start/stop/monitor each resource. We write custom agents for sigtran, routesphere, etc. |
| **corosync-qdevice** (optional) | Quorum tie-breaker for 2-node | A lightweight daemon on a 3rd host that participates in quorum votes, giving a 2-node cluster an odd vote count. |

### Architecture

```
              Floating VIP (what remote peers connect to)
                           |
          +----------------+----------------+
          |                |                |
     +----+----+     +----+----+     +----+----+
     |  Node 1 |     |  Node 2 |     |  Node 3 |
     |  ACTIVE |     | STANDBY |     | STANDBY |
     |         |     |         |     |         |
     | Corosync|<--->| Corosync|<--->| Corosync|
     |Pacemaker|     |Pacemaker|     |Pacemaker|
     |         |     |         |     |         |
     | Service |     | (idle)  |     | (idle)  |
     | VIP: Y  |     | VIP: N  |     | VIP: N  |
     +---------+     +---------+     +---------+

     Quorum: 3 nodes = majority of 2 required
     If Node 1 fails: Node 2 gets VIP + starts service
     If network partitions Node 1 vs (2+3): majority side wins
```

For 2-node clusters, add a qdevice on any existing server:

```
     +--------+     +--------+     +----------+
     | Node 1 |<--->| Node 2 |<--->| Qdevice  |
     | vote:1 |     | vote:1 |     | vote:1   |
     +--------+     +--------+     +----------+
                                   (lightweight,
                                    any server)
     Total votes: 3, majority: 2
```

### Failover Sequence

When the active node fails (detected by heartbeat timeout or health check failure):

```
1. Corosync detects node failure
   |
2. Quorum check — do surviving nodes have majority?
   |
   +-- NO  --> Surviving nodes go to standby (prevent split-brain)
   +-- YES --> Continue
   |
3. STONITH fences the failed node (power off / isolate)
   |
4. Pacemaker promotes a standby node, executes resource group in order:
   |
   a. ip_assign    — Assign VIP to new active node's interface
   b. arp_announce  — Send gratuitous ARP (peers update MAC cache)
   c. service_start — Start sigtran (or other service)
   d. post_check    — Verify service is healthy (M3UA ASP UP, etc.)
   |
5. Remote peers reconnect to same VIP, traffic resumes
   |
   Total time: 5-15 seconds typical
```

### Custom Health Probes (OCF Resource Agents)

Pacemaker calls resource agents at configurable intervals. Each agent is a shell script implementing `start`, `stop`, `monitor` actions.

Example probe categories for sigtran:

| Probe | What it checks | Failure action |
|-------|---------------|----------------|
| **Process** | JVM PID alive for each of 4 instances | Restart locally, then failover |
| **M3UA** | `grep "M3ua connection is active" logs/*.log` | Failover if ASP DOWN > threshold |
| **Redis** | `redis-cli -h sentinel -p 26380 SENTINEL master mymaster` | Failover |
| **Kafka** | Broker API version check against bootstrap servers | Failover |
| **SCTP** | `ss -tna` for SCTP associations to remote peers | Failover |
| **REST** | `curl http://localhost:port/health` | Failover |
| **SSH** | SSH to peer node, verify reachability | Alert (not failover) |
| **SIP** | SIP OPTIONS ping to proxy | Failover (for SIP services) |

### Alternatives Considered

| Tool | Pros | Cons | Verdict |
|------|------|------|---------|
| **Keepalived (VRRP)** | Simple, fast VIP failover | No real quorum for 3+ nodes. Split-brain risk in network partitions. No resource ordering. No fencing. | Too simple for our needs |
| **HashiCorp Consul** | Raft consensus, rich health checks, service discovery | Doesn't natively manage VIPs or service lifecycle. Requires custom glue for IP failover and service start/stop. | Good for service discovery, not for active/passive HA |
| **etcd / ZooKeeper** | Strong consensus primitives, leader election | Very low-level. Would need to build VIP management, fencing, resource ordering on top — essentially reimplementing Pacemaker. | Too much custom work |
| **Kubernetes** | Full orchestration, self-healing | Massive operational overhead for 2-3 node clusters. Overkill. SS7/SCTP workloads don't fit container model well. | Wrong tool for this job |
| **DRBD + Pacemaker** | Adds shared storage replication | We don't need shared storage (sigtran is stateless, state is in Redis). Adds unnecessary complexity. | Not needed |

### Per-Service Resource Agents

The same Corosync+Pacemaker cluster can manage multiple services. Each service gets its own resource agent:

| Service | Resource Agent | VIP | Health Probe |
|---------|---------------|-----|-------------|
| **Sigtran** | `ocf:telcobright:sigtran` | 1 VIP per M3UA link set | M3UA ASP status, SCTP associations |
| **Routesphere** | `ocf:telcobright:routesphere` | 1 VIP | REST /health, database connectivity |
| **SIP Proxy** | `ocf:telcobright:sipproxy` | 1 VIP | SIP OPTIONS, registration count |
| **Config Manager** | `ocf:telcobright:configmgr` | 1 VIP | REST /api/version |

### Cluster Topology Per Tenant

| Tenant | Current Setup | HA Target |
|--------|--------------|-----------|
| **bdcom** | 1 VM (bdcom1), 4 sigtran instances | 2 VMs + qdevice (or 3 VMs) |
| **btcl** | 1 VM (dell-sms-master), 4 sigtran instances | 2 VMs + qdevice (or 3 VMs) |
| **link3** | 1 VM (link3-1), 4 sigtran instances | 2 VMs + qdevice (or 3 VMs) |

Each tenant cluster is independent. VIPs per tenant:
- Sigtran needs one VIP per M3UA association pair (the `localHost` in sigtran config). Currently bdcom uses `10.255.246.173`, btcl uses `10.246.7.101` / `10.246.7.102`.

### Implementation Phases

**Phase 1: Foundation**
- Install Corosync + Pacemaker on 2 test VMs
- Configure cluster with quorum (+ qdevice if 2-node)
- Set up STONITH fencing
- Test basic VIP failover

**Phase 2: Sigtran Resource Agent**
- Write OCF resource agent for sigtran (start/stop/monitor)
- Integrate existing `start-all-sigtran.sh` / `stop-all-sigtran.sh`
- Configure resource group: VIP + sigtran + health monitors
- Test failover with M3UA ASP recovery timing

**Phase 3: Health Probes**
- Implement custom monitor scripts (M3UA, Redis, Kafka, SCTP)
- Configure probe intervals and failure thresholds
- Test probe-triggered failover

**Phase 4: Production Pilot**
- Deploy to one tenant (bdcom — simplest topology)
- Monitor failover behavior under real SS7 traffic
- Measure failover time and message loss

**Phase 5: Rollout**
- Deploy to btcl and link3
- Add resource agents for routesphere and other services
- Document operational procedures (manual failover, maintenance mode, adding nodes)

### Key Decisions Needed

1. **2-node + qdevice vs 3-node?** — 3-node is simpler (natural quorum) but costs more resources.
2. **Fencing method?** — IPMI (if baremetal), libvirt (if KVM VMs), SSH-based (last resort).
3. **Which tenant to pilot?** — bdcom is simplest (single `localHost` IP).
4. **Multiple VIPs per tenant?** — btcl uses 2 IPs (`10.246.7.101` for borak, `10.246.7.102` for khawaja). Both must move together or split across node pairs.
5. **Service on standby nodes?** — Keep sigtran stopped on standby (Pacemaker starts it on failover) or keep it running but without VIP (faster failover, but uses resources)?
