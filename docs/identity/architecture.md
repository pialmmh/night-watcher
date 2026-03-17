# Identity Management — Architecture

## Goal

Keycloak as the identity backend. Custom React UI (no Keycloak UI exposed). JWT-based SSO for all web apps proxied through Nginx.

## Current Status

- **Implemented**: Login page, Profile page, User Management page, AuthContext, ProtectedRoute, Keycloak REST API helpers
- **Not yet deployed**: Keycloak service itself (needs to be added to Dockerfile + supervisord)
- **Planned**: 2FA (TOTP + email OTP), role management UI, session management UI

## Architecture

```
User → Nginx (:80/443)
         │
         ├── /auth/*     → Keycloak (:7104)    ← token endpoint, admin API
         ├── /dashboard/* → Dashboard (:7100)   ← React SPA
         └── /api/*       → Backend apps        ← JWT validated by Nginx or app
```

## Keycloak Configuration

| Setting | Value |
|---------|-------|
| Realm | `night-watcher` |
| Client | `nw-dashboard` |
| Client type | Public SPA (no client secret) |
| Access type | Direct access grants (username/password → token) |
| Port | 7104 (internal, proxied at /auth/) |
| Database | MySQL (`keycloak` database) or embedded H2 |

### Roles

| Role | Access |
|------|--------|
| `admin` | Full: user management, config, all pages |
| `operator` | View dashboards, trigger manual failover |
| `viewer` | Read-only dashboard access |

## Keycloak REST APIs Used

### Token Endpoints (public)

```
POST /auth/realms/night-watcher/protocol/openid-connect/token
  grant_type=password → { access_token, refresh_token }
  grant_type=refresh_token → { access_token, refresh_token }

POST /auth/realms/night-watcher/protocol/openid-connect/logout
  refresh_token → invalidate session
```

### Account API (bearer token)

```
GET  /auth/realms/night-watcher/account → user info
POST /auth/realms/night-watcher/account/credentials/password → change password
```

### Admin API (admin role required)

```
GET/POST/PUT/DELETE /auth/admin/realms/night-watcher/users[/{id}]
PUT /auth/admin/realms/night-watcher/users/{id}/reset-password
GET /auth/admin/realms/night-watcher/roles
GET/POST /auth/admin/realms/night-watcher/users/{id}/role-mappings/realm
```

## React Auth Implementation

### Files

| File | Purpose |
|------|---------|
| `src/auth/keycloak.js` | All Keycloak REST API calls: login, refresh, logout, listUsers, createUser, updateUser, deleteUser, resetPassword, listRoles, getUserRoles, assignRoles |
| `src/auth/AuthContext.jsx` | React context providing: user, accessToken, isAuthenticated, roles, isAdmin, login(), logout(), getToken(). Auto-refreshes token 60s before expiry. |
| `src/auth/ProtectedRoute.jsx` | Route wrapper: redirects to /login if not authenticated, redirects to / if requireAdmin but user is not admin |

### Token Storage

- `sessionStorage` (not localStorage) — cleared when tab closes
- Keys: `access_token`, `refresh_token`
- Token parsed client-side via `parseJwt()` (base64 decode, no verification)
- Actual verification happens server-side (Keycloak validates on refresh, Nginx validates for API protection)

### Login Flow

```
/login page → POST /auth/.../token (grant_type=password)
  → success: save tokens, redirect to original URL
  → failure: show error message

Auto-refresh: setTimeout 60s before exp → POST /auth/.../token (grant_type=refresh_token)
  → success: save new tokens
  → failure: clear tokens, redirect to /login
```

## Deployment (TODO)

### Supervisord Entry

```ini
[program:keycloak]
command=/opt/keycloak/bin/kc.sh start
  --http-port=7104
  --hostname-strict=false
  --proxy=edge
  --db=mysql
  --db-url=jdbc:mysql://%(ENV_MYSQL_HOST)s:3306/keycloak
  --db-username=%(ENV_MYSQL_USER)s
  --db-password=%(ENV_MYSQL_PASSWORD)s
priority=11
autorestart=true
```

### Nginx Route

```nginx
location /auth/ {
    proxy_pass http://127.0.0.1:7104/auth/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

## Future: 2FA Integration

Two design docs exist (see `docs/2fa-web-design.md` and `docs/2fa-ssh-design.md`):

- **Web 2FA**: Was originally a standalone service on port 7103. With Keycloak, we can use Keycloak's built-in TOTP support instead — enable "OTP" required action in the realm.
- **SSH 2FA**: Google Authenticator PAM module on each host, independent of Keycloak.
