# Keycloak LXC Container

## Image Spec

| Field | Value |
|-------|-------|
| Base | Debian 12 |
| Java | OpenJDK 21 |
| Keycloak | 24.x (downloaded from keycloak.org) |
| Port | 7104 |
| IP | 10.10.19x.30 |
| Memory | 512MB–1GB |
| CPU | 1–2 cores |
| Database | External MySQL (keycloak database) |
| Image path | `orchestrix/images/lxc/keycloak-v.1.0.0/` |

## Build Steps (build.sh)

1. Launch temp Debian 12 container
2. Install: `openjdk-21-jdk-headless`, `curl`, `unzip`
3. Download Keycloak 24.x tarball from GitHub releases
4. Extract to `/opt/keycloak/`
5. Create systemd service unit for Keycloak
6. Export container as tarball artifact

## Launch Config (sample.conf)

```bash
CONTAINER_NAME="keycloak-prod"
BASE_IMAGE="keycloak-base-v.1.0.0"
BRIDGE_NAME="lxdbr0"
CONTAINER_IP="10.10.199.30/24"
GATEWAY_IP="10.10.199.1"
MEMORY_LIMIT="1GB"
CPU_LIMIT="2"

# Keycloak
KC_HTTP_PORT="7104"
KC_HOSTNAME_STRICT="false"
KC_PROXY="edge"
KC_DB="mysql"
KC_DB_URL="jdbc:mysql://10.10.199.10:3306/keycloak"
KC_DB_USERNAME="keycloak"
KC_DB_PASSWORD="changeme"

# Admin bootstrap (first start only)
KC_ADMIN_USER="admin"
KC_ADMIN_PASSWORD="admin"
```

## Systemd Service

```ini
[Unit]
Description=Keycloak Identity Server
After=network.target

[Service]
Type=simple
User=keycloak
ExecStart=/opt/keycloak/bin/kc.sh start \
  --http-port=${KC_HTTP_PORT} \
  --hostname-strict=${KC_HOSTNAME_STRICT} \
  --proxy=${KC_PROXY} \
  --db=${KC_DB} \
  --db-url=${KC_DB_URL} \
  --db-username=${KC_DB_USERNAME} \
  --db-password=${KC_DB_PASSWORD}
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

## Realm Setup (night-watcher)

After first start, create realm via admin API or admin console:

| Setting | Value |
|---------|-------|
| Realm | `night-watcher` |
| Client | `nw-dashboard` (public SPA, direct access grants) |
| Roles | `admin`, `operator`, `btrc`, `readonly`, `reseller`, `webrtc`, `smssender` |
| Password policy | 8+ chars, 1 uppercase, 1 digit |
| Brute force | Lock after 5 failures for 5 min |
| Access token TTL | 300s (5 min) |
| SSO session max | 28800s (8 hours) |

## Test Users (dev only)

| Username | Password | Role |
|----------|----------|------|
| admin | Admin1234 | admin |
| operator | Operator1234 | operator |

## MySQL Setup

```sql
CREATE DATABASE keycloak CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'keycloak'@'%' IDENTIFIED BY 'changeme';
GRANT ALL PRIVILEGES ON keycloak.* TO 'keycloak'@'%';
```

Keycloak auto-creates all its tables on first start.

## Health Check

```bash
curl -sf http://10.10.19x.30:7104/realms/night-watcher/.well-known/openid-configuration
```
