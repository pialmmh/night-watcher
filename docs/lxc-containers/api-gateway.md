# API Gateway LXC Container

## Image Spec

| Field | Value |
|-------|-------|
| Base | Debian 12 |
| Java | OpenJDK 21 |
| Framework | Spring Cloud Gateway 2023.0.1 / Spring Boot 3.2.5 |
| Port | 8001 |
| IP | 10.10.19x.40 |
| Memory | 256MB–512MB |
| CPU | 1–2 cores |
| Database | External MySQL (gateway_audit.audit_log table) |
| Source | `/home/mustafa/telcobright-projects/routesphere/api-gateway` |
| Image path | `orchestrix/images/lxc/api-gateway-v.1.0.0/` |

## Build Steps (build.sh)

1. Launch temp Debian 12 container
2. Install: `openjdk-21-jdk-headless`, `curl`
3. Copy pre-built JAR (`target/gateway-0.0.1-SNAPSHOT.jar`) into `/opt/api-gateway/`
4. Create systemd service unit
5. Export container as tarball artifact

**Note**: Build the JAR on the dev machine (`mvn clean package -DskipTests`), then copy the JAR into the container. Do NOT install Maven inside the container.

## Launch Config (sample.conf)

```bash
CONTAINER_NAME="api-gateway-prod"
BASE_IMAGE="api-gateway-base-v.1.0.0"
BRIDGE_NAME="lxdbr0"
CONTAINER_IP="10.10.199.40/24"
GATEWAY_IP="10.10.199.1"
MEMORY_LIMIT="512MB"
CPU_LIMIT="2"

# Spring profiles
SPRING_PROFILES="nw-local"

# Keycloak (token validation)
KC_USERINFO_URL="http://10.10.199.30:7104/realms/night-watcher/protocol/openid-connect/userinfo"

# MySQL (audit log)
MYSQL_HOST="10.10.199.10"
MYSQL_PORT="3306"
MYSQL_USER="root"
MYSQL_PASSWORD="changeme"
MYSQL_DATABASE="gateway_audit"

# Eureka (optional — disable for standalone)
EUREKA_ENABLED="false"
EUREKA_URL="http://localhost:8761/eureka"
```

## Application Profile (nw-local)

File: `src/main/resources/application-nw-local.properties`

```properties
spring.r2dbc.url=r2dbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?...
spring.r2dbc.username=${MYSQL_USER}
spring.r2dbc.password=${MYSQL_PASSWORD}
security.token-validity-check-url=${KC_USERINFO_URL}
eureka.client.enabled=${EUREKA_ENABLED}
spring.cloud.discovery.enabled=${EUREKA_ENABLED}
spring.cloud.gateway.discovery.locator.enabled=${EUREKA_ENABLED}
```

## Systemd Service

```ini
[Unit]
Description=Routesphere API Gateway
After=network.target

[Service]
Type=simple
User=gateway
WorkingDirectory=/opt/api-gateway
ExecStart=/usr/bin/java -jar /opt/api-gateway/gateway.jar \
  --spring.profiles.active=${SPRING_PROFILES} \
  --security.token-validity-check-url=${KC_USERINFO_URL} \
  --spring.r2dbc.url=r2dbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE} \
  --spring.r2dbc.username=${MYSQL_USER} \
  --spring.r2dbc.password=${MYSQL_PASSWORD}
Restart=on-failure
RestartSec=10
Environment=JAVA_OPTS=-Xms128m -Xmx256m

[Install]
WantedBy=multi-user.target
```

## Policy Architecture

8 policies defined in `config/ccl/AppConfig.java`:

| Policy | Role | Endpoints | Data Rules |
|--------|------|-----------|------------|
| Allow (Super Admin) | ROLE_ADMIN, ROLE_SMSADMIN | All | None |
| CallingPortalUser | ROLE_USER | 315+ | 9 rules (partner/user field matching) |
| BtrcUser | ROLE_BTRC | 11 | None |
| ReadOnly | ROLE_READONLY | 24 | None |
| Reseller | ROLE_RESELLER | 90+ | None |
| WebRtc | ROLE_WEBRTC | 7 | None |
| SmsSender | ROLE_SMSSENDER | 1 | None |
| Deny | — | 0 | — |

216 public endpoints bypass JWT entirely (defined in `PublicEndpointRegistry.java`).

## Auth Flow

```
Request → Gateway(:8001)
  → extract Bearer token from Authorization header
  → GET ${KC_USERINFO_URL} with Bearer token
  → Keycloak returns user info (sub, email, realm_access.roles)
  → map roles to AuthUser DTO
  → match policy by role
  → check endpoint in policy's allowedEndpoints
  → if data access rules exist: validate payload fields
  → audit log (async to MySQL)
  → forward to backend service
```

## MySQL Setup

```sql
CREATE DATABASE gateway_audit;
USE gateway_audit;
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    userIdentifier VARCHAR(255),
    action VARCHAR(500),
    timestamp DATETIME,
    response VARCHAR(10),
    details VARCHAR(500)
);
```

## Health Check

```bash
# Gateway responds 500 on / (no default route — this is normal)
# Test with a valid Keycloak token:
TOKEN=$(curl -s -X POST "http://keycloak:7104/realms/night-watcher/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=nw-dashboard&username=admin&password=Admin1234" | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://api-gateway:8001/SOME-SERVICE/health
```

## Rebuilding

```bash
cd /home/mustafa/telcobright-projects/routesphere/api-gateway
mvn clean package -DskipTests
# Copy target/gateway-0.0.1-SNAPSHOT.jar into the container:
lxc file push target/gateway-0.0.1-SNAPSHOT.jar api-gateway-prod/opt/api-gateway/gateway.jar
lxc exec api-gateway-prod -- systemctl restart api-gateway
```
