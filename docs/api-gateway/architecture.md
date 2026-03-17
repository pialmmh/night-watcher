# API Gateway Integration — Architecture

## Goal

Integrate the existing Telcobright API Gateway (Spring Cloud Gateway, Java 21) with night-watcher for centralized access control visibility and management. Build a custom UI in the dashboard. Connect to Keycloak for token validation.

## Existing Gateway Summary

- **Framework**: Spring Cloud Gateway (reactive WebFlux) on port 8001
- **Auth**: External token validation → maps roles → policy matching → endpoint check → data-level authz
- **8 Policies**: Allow (admin), CallingPortalUser, BtrcUser, ReadOnly, Reseller, WebRtc, SmsSender, Deny
- **216 public endpoints** (no JWT), **315+ protected endpoints** (widest policy)
- **9 data access rules** (JSON Path field matching for row-level security)
- **Audit**: All requests logged to MySQL `audit_log` table (async with retry)
- **Source**: `/home/mustafa/telcobright-projects/RTC-Manager/API-Gateway/`

## Keycloak Integration

Point gateway's token validation at Keycloak:

```properties
# Before (custom auth service)
security.token-validity-check-url=http://localhost:8080/auth/validateAccess?token=

# After (Keycloak userinfo endpoint)
security.token-validity-check-url=http://keycloak:7104/auth/realms/night-watcher/protocol/openid-connect/userinfo
```

Keycloak JWT `realm_access.roles` maps to gateway's `AuthRole` objects. Existing policy matching works unchanged.

## Dashboard Pages

| Page | Route | Purpose |
|------|-------|---------|
| Gateway Overview | `/gateway` | Health, request stats, active policies |
| Policies | `/gateway/policies` | View all 8 policies with their allowed endpoints |
| Audit Logs | `/gateway/audit` | Search/filter audit_log table |
| Data Access Rules | `/gateway/rules` | View field-level authorization rules |
| Public Endpoints | `/gateway/public` | View 216 public endpoints |
| Keycloak Admin | `/gateway/keycloak` | Realm config, clients, roles, sessions |
