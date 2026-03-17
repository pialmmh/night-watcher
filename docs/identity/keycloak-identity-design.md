# Keycloak Identity Management — Design

## Overview

Keycloak runs as a backend service in night-watcher. We build custom React UIs (matching the existing MUI dashboard) that talk to Keycloak's REST APIs. All web apps get JWT-based SSO via Nginx + OIDC.

## Architecture

```
                    ┌──────────────────────────────┐
                    │         Nginx (:80/443)       │
                    │                               │
                    │  /auth/*    → Keycloak :8080   │
                    │  /dashboard → Dashboard :7100  │
                    │  /admin/*   → Backend apps     │
                    │                               │
                    │  All protected locations:      │
                    │  check JWT from Keycloak       │
                    └──────────────┬────────────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │     Keycloak (:8080)       │
                    │                            │
                    │  OIDC / OAuth2 provider    │
                    │  User management           │
                    │  Role management           │
                    │  2FA (TOTP, email)          │
                    │  Session management         │
                    │  JWT token issuing          │
                    │                            │
                    │  DB: embedded H2 or MySQL   │
                    │  (security_monitoring DB)   │
                    └────────────────────────────┘
```

## What Keycloak Handles (backend only, no Keycloak UI exposed)

- User CRUD (create, update, delete, search)
- Authentication (username/password → JWT)
- Token refresh, logout, session invalidation
- Role and group management
- 2FA enrollment and verification (TOTP)
- Password policies (complexity, expiry)
- Brute force protection
- Email verification and password reset

## What We Build (custom React UI)

### Phase 1 — Basic JWT Identity (minimal)

| Page | Route | Purpose |
|------|-------|---------|
| Login | `/login` | Username + password → get JWT |
| User Profile | `/profile` | View/edit own profile, change password |
| User Management | `/users` | Admin: list, create, edit, delete users |

### Phase 2 — Extended (later)

| Page | Route | Purpose |
|------|-------|---------|
| 2FA Setup | `/profile/2fa` | Enroll TOTP, show QR code |
| Role Management | `/roles` | Admin: create/assign roles |
| Sessions | `/sessions` | Admin: view/kill active sessions |
| Login History | `/login-history` | Audit: login attempts |

## Keycloak REST APIs Used

### Auth (public endpoints)
```
POST /auth/realms/{realm}/protocol/openid-connect/token
  → { username, password, grant_type: "password", client_id }
  ← { access_token, refresh_token, expires_in }

POST /auth/realms/{realm}/protocol/openid-connect/token
  → { refresh_token, grant_type: "refresh_token", client_id }
  ← { access_token, refresh_token }

POST /auth/realms/{realm}/protocol/openid-connect/logout
  → { refresh_token, client_id }
```

### User Profile (bearer token required)
```
GET  /auth/realms/{realm}/account
PUT  /auth/realms/{realm}/account
POST /auth/realms/{realm}/account/credentials/password
  → { currentPassword, newPassword }
```

### Admin API (admin role required)
```
GET    /auth/admin/realms/{realm}/users
GET    /auth/admin/realms/{realm}/users/{id}
POST   /auth/admin/realms/{realm}/users
PUT    /auth/admin/realms/{realm}/users/{id}
DELETE /auth/admin/realms/{realm}/users/{id}
PUT    /auth/admin/realms/{realm}/users/{id}/reset-password
GET    /auth/admin/realms/{realm}/users/{id}/role-mappings/realm
POST   /auth/admin/realms/{realm}/users/{id}/role-mappings/realm
```

## JWT Flow

```
1. User opens /dashboard
       │
       ▼
2. React app checks localStorage for access_token
       │
   No token → redirect to /login
       │
       ▼
3. Login page: POST /auth/realms/night-watcher/protocol/openid-connect/token
   { username, password, grant_type: "password", client_id: "nw-dashboard" }
       │
       ▼
4. Keycloak returns { access_token (JWT), refresh_token, expires_in }
       │
       ▼
5. Store tokens in localStorage/memory
   Set Authorization: Bearer <token> on all API requests
       │
       ▼
6. On 401 → try refresh_token → if fails → redirect to /login

7. Nginx can also validate JWT for backend API protection:
   location /api/ {
       auth_jwt "night-watcher";
       auth_jwt_key_file /etc/nginx/keycloak-public-key.pem;
       proxy_pass http://backend;
   }
```

## Keycloak Configuration

### Realm: night-watcher
### Client: nw-dashboard
- Client Protocol: openid-connect
- Access Type: public (SPA, no client secret)
- Direct Access Grants: enabled (for username/password login)
- Valid Redirect URIs: https://*/dashboard/*
- Web Origins: *

### Default Roles
- `admin` — full access (user management, config)
- `operator` — view dashboards, trigger manual failover
- `viewer` — read-only dashboard access

### Realm Settings
- Login: username/password
- 2FA: optional TOTP (Phase 2)
- Password policy: 8+ chars, 1 uppercase, 1 number
- Brute force: lock after 5 failed attempts for 5 min

## Deployment in Night-Watcher

```
supervisord.conf:
    [program:keycloak]
    command=/opt/keycloak/bin/kc.sh start
      --http-port=8080
      --hostname-strict=false
      --proxy=edge
      --db=mysql
      --db-url=jdbc:mysql://%(ENV_MYSQL_HOST)s:3306/keycloak
      --db-username=%(ENV_MYSQL_USER)s
      --db-password=%(ENV_MYSQL_PASSWORD)s
    priority=11
    autorestart=true

Nginx:
    location /auth/ {
        proxy_pass http://127.0.0.1:8080/auth/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
```

### MySQL Schema (auto-created by Keycloak)
- Keycloak manages its own tables in a dedicated `keycloak` database
- No manual schema needed

## File Layout

```
dashboard/src/
├── auth/
│   ├── AuthContext.jsx      ← React context: token state, login/logout/refresh
│   ├── AuthProvider.jsx     ← wraps app, handles token lifecycle
│   ├── ProtectedRoute.jsx   ← redirects to /login if no valid token
│   └── keycloak.js          ← API helpers: login(), refresh(), logout()
│
├── pages/
│   ├── Login.jsx            ← username/password form
│   ├── Profile.jsx          ← view/edit own profile, change password
│   └── UserManagement.jsx   ← admin: list/create/edit/delete users
│
├── App.jsx                  ← add auth routes + ProtectedRoute wrapper
└── ...existing pages...
```

## Port

Keycloak: **7104** (internal, proxied through Nginx at /auth/)

Follows night-watcher port convention (7100 range).
