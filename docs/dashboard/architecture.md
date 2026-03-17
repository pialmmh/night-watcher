# Dashboard — Architecture

## Goal

React SPA for security monitoring and HA cluster management. Runs on port 7100 (Vite dev), served via Nginx in production. JWT-authenticated via Keycloak.

## Tech Stack

- React 18.2 + Vite 5.2
- Material-UI (MUI) 5.15
- React Router DOM 6.22
- Recharts 2.12 (charts)
- Day.js 1.11 (date handling)

## Pages

| Page | Route | Access | Purpose |
|------|-------|--------|---------|
| Login | `/login` | Public | Username + password → Keycloak JWT |
| Overview | `/` | Authenticated | Security event counts, trends, MITRE tactics |
| Modules | `/modules` | Authenticated | Process status from module-status-api (:7101) |
| Log Explorer | `/logs` | Authenticated | Search Wazuh alerts via OpenSearch |
| Security Events | `/security` | Authenticated | Security event details |
| WAF | `/waf` | Authenticated | ModSecurity WAF events |
| Watchdog | `/watchdog` | Authenticated | Backend health check status |
| Network | `/network` | Authenticated | Network topology |
| HA Cluster | `/ha` | Authenticated | Cluster status, node observations, SDOWN/ODOWN |
| Profile | `/profile` | Authenticated | View account info, change password |
| User Management | `/users` | Admin only | List, create, edit, delete users |

## Auth System

### Files

```
dashboard/src/auth/
├── keycloak.js          # Keycloak REST API helpers (login, refresh, logout, admin CRUD)
├── AuthContext.jsx       # React context: token state, auto-refresh, roles
└── ProtectedRoute.jsx   # Route guard (redirect to /login, optional admin check)
```

### Token Flow

1. Login page POSTs to Keycloak token endpoint (grant_type=password)
2. Keycloak returns `access_token` (JWT) + `refresh_token`
3. Stored in `sessionStorage` (cleared on tab close)
4. `AuthContext` auto-refreshes token 60s before expiry
5. All API requests include `Authorization: Bearer <token>`
6. On 401 or refresh failure → clear tokens → redirect to `/login`

### JWT Claims Used

```json
{
  "sub": "user-uuid",
  "preferred_username": "admin",
  "name": "Admin User",
  "email": "admin@example.com",
  "realm_access": {
    "roles": ["admin", "operator"]
  },
  "exp": 1234567890
}
```

### Role-Based Access

- `admin` — sees "Users" nav item, can access `/users`
- `operator` — all pages except user management
- `viewer` — read-only (future enforcement)

Roles extracted client-side from `realm_access.roles` in the JWT.

## Layout

- Left drawer (220px): nav items + Profile/Users/Logout at bottom
- AppBar: "Security Dashboard" title + username on the right
- Content area: grey.50 background, 2-unit padding

## API Dependencies

| API | Port | Used By |
|-----|------|---------|
| OpenSearch | 9200 | Log Explorer, Overview, Security Events |
| Module Status API | 7101 | Modules page |
| hactl Status API | 7102 | HA Cluster page |
| Keycloak | 7104 (via /auth/) | Login, Profile, User Management |

## Build

```bash
cd dashboard
npm install
npm run dev      # dev server on :7100
npm run build    # production build → dist/
```

## Style Conventions

- MUI theme: primary=#1565c0, fontSize=13
- Table cells: compact padding (6px 12px)
- Forms: use enough left/right padding, keep vertically compact
- No emojis unless requested
