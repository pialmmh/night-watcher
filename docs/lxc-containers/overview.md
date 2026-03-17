# LXC Container Deployment — Overview

## Three Containers

The Routesphere operations stack runs as 3 separate LXC containers per server cluster:

```
┌─────────────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│ night-watcher (LXC)         │  │ keycloak (LXC)       │  │ api-gateway (LXC)    │
│                             │  │                      │  │                      │
│ Security + HA + Dashboard   │  │ Identity Management  │  │ Policy-based Routing │
│ 10.10.19x.200               │  │ 10.10.19x.30         │  │ 10.10.19x.40         │
│ ~2GB RAM                    │  │ ~512MB RAM           │  │ ~256MB RAM           │
│                             │  │                      │  │                      │
│ Nginx+WAF, Wazuh, hactl,   │  │ Keycloak 24          │  │ Spring Cloud Gateway │
│ CrowdSec, Fail2Ban,        │  │ Java 21              │  │ Java 21              │
│ Consul, Dashboard (React)   │  │ MySQL (external)     │  │ R2DBC MySQL (audit)  │
│                             │  │ Port: 7104           │  │ Port: 8001           │
│ Ports: 80,443,5601,7100-02 │  │                      │  │                      │
└─────────────────────────────┘  └──────────────────────┘  └──────────────────────┘
         │                               │                          │
         └───────────────────────────────┼──────────────────────────┘
                                         │
                                    lxdbr0 bridge
                                    10.10.19x.1/24
```

## Why 3 Containers (Not 1)

| Concern | Night-Watcher | Keycloak | API Gateway |
|---------|--------------|----------|-------------|
| **Lifecycle** | Always on, per server | Shared across cluster, rarely restarted | Restarts on policy changes |
| **Memory** | ~2GB (Wazuh heavy) | ~512MB | ~256MB |
| **Scaling** | One per server (HA) | One per cluster (active-standby) | Can scale horizontally |
| **Failure** | Lose monitoring | Lose login (tokens valid until expiry) | Lose API routing |
| **Updates** | Security patches | Keycloak version upgrades | Code deploys |

## IP Allocation Convention

Per the networking guideline (`orchestrix/images/networking_guideline_claude.md`):

```
10.10.19x.0/24 per host (x decrements: 199, 198, 197...)

.1          lxdbr0 bridge gateway
.10-.12     MySQL (primary/secondary/tertiary)
.15-.16     PostgreSQL
.18-.19     Redis
.20-.22     Kafka brokers
.25-.27     ZooKeeper
.30         Keycloak                    ← new
.40         API Gateway                 ← new
.50-.53     Nginx / HAProxy / LB
.70-.75     Consul / etcd
.100-.199   Docker containers
.200-.254   LXC containers (night-watcher at .200)
```

## Container Communication

```
Dashboard(:7100) ──/auth/──► Keycloak(:7104)      JWT login/refresh/admin
Dashboard(:7100) ──/api/───► API Gateway(:8001)    proxied API calls
API Gateway(:8001) ─token──► Keycloak(:7104)       token validation (userinfo)
API Gateway(:8001) ─route──► Backend services       via Eureka or static routes
API Gateway(:8001) ─audit──► MySQL(:3306)           audit_log table
Night-Watcher hactl ─SSH──► Host                    VIP/service management
Night-Watcher ──consul──►   Consul cluster          HA coordination
```

## Image Build Convention

Each container image follows this structure:

```
orchestrix/images/lxc/<name>-v.<version>/
├── build/
│   ├── build.conf          ← image name, version, base image, source paths
│   ├── build.sh            ← creates temp Debian 12 container, installs everything, exports tarball
│   └── files/              ← config templates, scripts to copy into image
└── generated/
    ├── launch.sh           ← usage: ./launch.sh <config-file>
    ├── sample.conf         ← all configurable parameters with defaults
    └── artifact/
        └── <name>-v<ver>-<timestamp>.tar.gz   ← exportable image
```

## Lifecycle Commands

```bash
# Build image (one-time)
cd orchestrix/images/lxc/keycloak-v.1.0.0/build
./build.sh

# Launch container from image
cd orchestrix/images/lxc/keycloak-v.1.0.0/generated
./launch.sh /path/to/my-keycloak.conf

# Standard LXC operations
lxc start keycloak-prod
lxc stop keycloak-prod
lxc exec keycloak-prod -- bash
lxc delete keycloak-prod

# View logs
lxc exec keycloak-prod -- journalctl -u keycloak -f
```

## Local Development (No LXC)

For local dev/testing, all 3 run directly on the dev machine:

```bash
# Keycloak (already installed at /opt/keycloak)
/opt/keycloak/bin/kc.sh start-dev --http-port=7104

# API Gateway
cd /home/mustafa/telcobright-projects/routesphere/api-gateway
mvn clean package -DskipTests
java -jar target/gateway-0.0.1-SNAPSHOT.jar --spring.profiles.active=nw-local

# Dashboard
cd /home/mustafa/telcobright-projects/routesphere/night-watcher/dashboard
npm run dev
```

Local MySQL: `127.0.0.1:3306` (running in LXC at 10.20.0.123, root/123456)
