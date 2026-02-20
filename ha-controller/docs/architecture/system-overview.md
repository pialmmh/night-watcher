# HA Controller — System Overview

## Purpose

The HA Controller (`hactl`) is a custom high-availability control plane for the Telcobright Routesphere platform. It manages failover of telecom resources (VIPs, sigtran instances, SIP proxies, etc.) across cluster nodes, using HashiCorp Consul for quorum-backed leader election.

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  Consul Cluster                  │
│            (Raft consensus / KV store)           │
└──────────┬───────────────────┬──────────────────┘
           │                   │
    ┌──────┴──────┐     ┌──────┴──────┐
    │   Node 1    │     │   Node 2    │
    │  (Leader)   │     │ (Follower)  │
    │             │     │             │
    │  ┌────────┐ │     │  ┌────────┐ │
    │  │ Engine │ │     │  │ Engine │ │
    │  └───┬────┘ │     │  └───┬────┘ │
    │      │      │     │      │      │
    │  ┌───┴────┐ │     │  (waiting)  │
    │  │ Groups │ │     │             │
    │  │ ┌─VIP  │ │     │             │
    │  │ ├─Svc  │ │     │             │
    │  │ └─Chk  │ │     │             │
    │  └────────┘ │     │             │
    └─────────────┘     └─────────────┘
```

## Core Components

### Engine
The reconciliation loop. Every 5 seconds it:
1. Attempts to acquire the Consul leader lock
2. If leader: runs health checks on all resource groups
3. If follower: waits and monitors who the leader is
4. On leadership change: activates or deactivates resource groups

### Leader Election
Uses Consul sessions and KV locks. A session is created with a TTL; if the holder fails to renew, the lock is released after a configurable delay (preventing flapping).

### Resource Groups
Ordered collections of resources. On activation, resources start in order (VIP first, then services). On deactivation, the order reverses. If any resource fails to activate, already-activated resources are rolled back.

### Resources
Implementations of the Resource interface. Phase 1 includes:
- **VipResource**: Manages floating IPs via `ip addr add/del` + gratuitous ARP

Future phases will add: sigtran, routesphere, SIP proxy, config-manager.

### Executor
Abstraction for running commands locally or via SSH. Used by resources and health checks to interact with the system.

### Health Checks
Periodic probes (ping, TCP, HTTP, script) that verify resource health. Unhealthy results trigger escalation.

## Configuration

Single YAML file defines the cluster, nodes, resource groups, and health checks. Each node runs the same binary with `--node <id>` to identify itself.

## Failover Flow

```
1. Leader node fails (crash, network partition, health check failure)
2. Consul session expires after TTL (15s default)
3. Lock delay passes (5s default)
4. Follower acquires leader lock
5. New leader activates resource groups:
   a. Assign VIP to local interface
   b. Send gratuitous ARP
   c. Start services (future)
   d. Run post-start health checks
6. Remote peers reconnect to same VIP
```

Total failover time: ~20-25 seconds (session TTL + lock delay + activation).
