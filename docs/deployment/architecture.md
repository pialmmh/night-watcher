# Deployment — Architecture

## Goal

Night-watcher runs as an LXC container on each physical server in a Routesphere tenant cluster. One container per server, all containers form a Consul cluster for HA coordination.

## Container Technology

- **LXC/LXD** (not Docker) for production deployment
- Debian 12 base image
- Built via Dockerfile (3-stage: modsec-build + go-build + runtime)
- Launched via `launch.sh` per tenant per node

## Multi-Tenant, Multi-Node

```
BTCL Cluster (3 nodes):
  dell-sms-master  → night-watcher-btcl (LXC)  → hactl node btcl-nw-1
  dell-sms-slave   → night-watcher-btcl (LXC)  → hactl node btcl-nw-2
  sbc1             → night-watcher-btcl (LXC)  → hactl node btcl-nw-3

BDCOM Cluster (3 nodes):
  bdcom1           → night-watcher-bdcom (LXC)  → hactl node bdcom-nw-1
  bdcom2           → night-watcher-bdcom (LXC)  → hactl node bdcom-nw-2
  bdcom3           → night-watcher-bdcom (LXC)  → hactl node bdcom-nw-3
```

## Networking

Each night-watcher container:
- Primary IP on `lxdbr0` (e.g., 10.10.195.200)
- Optional secondary VIP for HA (e.g., 10.255.246.175)
- SSH access to host via lxdbr0 gateway for executing VIP/service commands
- WireGuard overlay routes for cross-host container communication

### IP Convention

- Container subnets: `10.10.19x.0/24` (decrement per host: 199, 198, 197...)
- LXD bridge gateway: `10.10.19x.1`
- Night-watcher container: `10.10.19x.200`
- WireGuard overlay: `10.9.9.x/24`

## Deploy Scripts

| Script | Purpose |
|--------|---------|
| `deploy.sh <tenant>` | Build image + push to all tenant servers |
| `launch.sh` | Launch LXC container with correct config |
| `deploy-bdcom.sh` | BDCOM-specific deploy |

## Per-Node Configuration

Each node needs:
- `tenant.conf` — tenant-level settings (MySQL, Wazuh creds)
- `ha-controller.yml` — cluster-wide HA config
- Node-specific env vars: `HACTL_NODE_ID`, `HACTL_ENABLED`

These are passed via:
1. Bind-mount config directory into the container
2. Environment variables set in LXC profile or launch command

## Production Servers

### BTCL

| Server | Alias | Underlay IP | Container Subnet | Role |
|--------|-------|-------------|------------------|------|
| dell-sms-master | sbc4 | 10.246.7.102 | 10.10.196.0/24 | Primary |
| dell-sms-slave | — | 10.246.7.103 | 10.10.195.0/24 | Secondary |
| sbc1 | — | (internal) | 10.10.194.0/24 | Witness |

### BDCOM

| Server | Underlay IP | Container Subnet |
|--------|-------------|------------------|
| bdcom1 | 10.255.246.173 | 10.10.199.0/24 |
| bdcom2 | 10.255.246.174 | 10.10.198.0/24 |
| bdcom3 | 10.255.246.171 | 10.10.197.0/24 |

## SSH Access

Use the ssh-automation wrapper:
```bash
/home/mustafa/telcobright-projects/routesphere/routesphere-core/tools/ssh-automation/servers/<tenant>/ssh <server> "command"
```

## Services That Need HA

| Service | Ports | HA Status |
|---------|-------|-----------|
| sigtran (SS7 MAP) | 8282 UDP | VIP + action failover via hactl |
| routesphere-core | 19999, 18093 | Future |
| FreeSWITCH | 5060 SIP | Future |

## Sigtran HA (BTCL — In Progress)

Planning phase. Key decisions pending:
- SCTP bind IPs: 10.246.7.102 (master) / 10.247.7.103 (slave) / 10.246.7.101 (floating VIP) — subnet TBD
- Sigtran runs in separate LXC container (`sigtran-btcl`) managed by hactl via `action` resources
- hactl SSHes to host → runs `lxc start/stop sigtran-btcl`
- VIP on lxdbr0 (secondary IP) for SCTP multi-homing

## Consul Deployment

Consul runs inside each night-watcher container:
- Agent mode (not server) — connects to Consul server cluster
- Used for: leader election, KV store (observations, active node, epoch)
- Config at `/etc/consul.d/`
- Started by supervisord (priority 12)
