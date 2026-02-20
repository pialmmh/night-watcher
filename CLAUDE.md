# High Availability Project

## Java Version
- JDK 21 required (same as routesphere)

## Routesphere Platform Context

This project builds HA (High Availability) modules for the Telcobright Routesphere platform. The routesphere codebase lives at `../` (parent directory). Reference it for architecture understanding but do not modify it from this project.

### What is Routesphere?

Multi-tenant telecom platform (Quarkus 3.26.1 / Java 21) handling SMS (A2P, P2P, OTP), Voice (FreeSWITCH ESL), and SIP routing. Runs as a single JVM per tenant in production.

### Key Services That Need HA

| Service | What it does | Current deployment | Ports |
|---------|-------------|-------------------|-------|
| **sigtran** | SS7 MAP gateway (SRI/MT via SCTP/M3UA). 4 JVM instances per tenant, each handling M3UA links to MNOs | Single VM per tenant | 8282 (UDP) |
| **routesphere-core** | SMS processing engine: REST API, Kafka consumer, state machines, retry scheduler, CDR | Single VM per tenant | 19999 (Quarkus), 18093 (V2 API) |
| **config-manager** | Spring Boot tenant config API. Routesphere fetches tenant config from it at startup | Single VM per tenant | 7071 (private IP) |
| **FreeSWITCH** | Voice/SIP B2BUA for call routing | Single VM per tenant | 5060 (SIP), 8021 (ESL) |

### SMS Flow (simplified)

```
HTTP API -> Pipeline validation -> Kafka queue -> SmsQueueConsumer
  -> SmscBean -> SigTranSmsRegistry (state machine pool, max 2000 concurrent)
  -> SigtranUdpClient (JSON/UDP) -> sigtran process (SS7 stack)
  -> Redis pub/sub response -> state machine completes
```

### Sigtran Communication

- routesphere -> sigtran: UDP JSON to port 8282
- sigtran -> routesphere: Redis pub/sub channels (`sriresponse-{tenant}`, `mtfsmpublisher-{tenant}`)
- Each sigtran instance handles SCTP associations to specific MNO MSCs/HLRs/SGWs
- Remote SS7 peers connect to a fixed IP (localHost in sigtran config) -- this IP MUST be stable (VIP)

### External Dependencies

| Service | Port | HA implications |
|---------|------|----------------|
| MySQL | 3306 | Per-tenant DB, already has master/slave replication |
| Kafka | 9092 | 3-node cluster, inherently HA |
| Redis Sentinel | 26380 | Already HA via Sentinel |
| sigtran | 8282 UDP | NOT HA -- single point of failure, needs VIP failover |

### Current Production Servers

| Tenant | Server | IP | Services on it |
|--------|--------|------------|---------------|
| btcl | dell-sms-master | 10.246.7.102 | routesphere + 4 sigtran + MySQL + Kafka + Redis |
| bdcom | bdcom1 | 10.255.246.173 | routesphere + 4 sigtran + MySQL + Kafka + Redis |
| link3 | link3-1 | (link3 net) | routesphere + 4 sigtran + MySQL + Kafka + Redis |

### Tenant Config Structure (reference)
```
routesphere-core/src/main/resources/config/tenants/{tenant}/{profile}/
  profile-{profile}.yml          <- DB, Redis, feature flags
  channels/sigtran/sigtran-main.yml  <- sigtran endpoints
  channels/omniqueue/omniqueue-main.yml  <- Kafka config
```

### Deployment Tools (reference)
```
routesphere-core/tools/deploy/remote-deploy-v2.sh  <- deploy script
routesphere-core/tools/deploy/tenant-conf-v2/      <- per-tenant server config
routesphere-core/tools/ssh-automation/servers/      <- SSH inventory
```

### Networking Conventions
- Container subnets: 10.10.x.0/24 per host (starting from 10.10.199.0, decrementing)
- WireGuard overlay: 10.9.9.x
- FRR/BGP announces container subnets across hosts
- VPN (WireGuard) for developer access, pushed route for 10.10.0.0/16

## Project Guidelines
- Do not git push until asked
- Use JDK 21
- Follow Maven conventions
- Reference `../routesphere-core/` for routesphere code (read-only)
