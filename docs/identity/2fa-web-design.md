# 2FA for Web Applications — Design

## Overview

Single 2FA service running inside the night-watcher container, enforced by Nginx for all configured web backends. No changes needed to individual backends except issuing `2fa_verified: false` in their JWT after login.

## Architecture

```
User → Nginx → any protected backend
                  │
                  ├── No JWT → pass through (backend handles login)
                  │
                  ├── JWT has 2fa_verified:true → proxy_pass to backend ✓
                  │
                  └── JWT has 2fa_verified:false
                        │
                        ▼
                  302 → /2fa/verify?rd=<original_url>
                        │
                        ▼
               ┌─────────────────────┐
               │  2FA Service        │
               │  (night-watcher)    │
               │  port 7103          │
               │                     │
               │  GET  /2fa/verify   │ ← renders TOTP/email input page
               │  POST /2fa/verify   │ ← checks code, re-signs JWT
               │  POST /2fa/email    │ ← sends email OTP
               │  GET  /2fa/setup    │ ← QR code for first-time TOTP
               │  POST /2fa/setup    │ ← confirms TOTP enrollment
               └──────────┬──────────┘
                          │
                    code correct?
                    │           │
                   YES          NO
                    │           │
                    ▼           ▼
              Re-sign JWT    "Invalid code,
              with            try again"
              2fa_verified:
              true
                    │
                    ▼
              Set cookie +
              302 → original_url
```

## Nginx Integration

Each protected app adds one line:

```nginx
location /dashboard/ {
    include snippets/require_2fa.conf;    # one line
    proxy_pass http://dashboard:7100;
}

location /admin/ {
    include snippets/require_2fa.conf;    # same one line
    proxy_pass http://admin:9000;
}

location /grafana/ {
    # no 2FA — intentionally unprotected
    proxy_pass http://grafana:3000;
}
```

### snippets/require_2fa.conf

```nginx
access_by_lua_block {
    local jwt = require("resty.jwt")
    local token = ngx.var.cookie_auth_token
        or ngx.req.get_headers()["Authorization"]

    if not token then
        -- no JWT, let backend handle login
        return
    end

    local obj = jwt:verify(SHARED_SECRET, token)

    if not obj.verified then
        return ngx.redirect("/2fa/verify?rd=" .. ngx.var.request_uri)
    end

    if obj.payload["2fa_verified"] ~= true then
        return ngx.redirect("/2fa/verify?rd=" .. ngx.var.request_uri)
    end

    -- valid + 2FA done, proceed
}
```

## Login Flow Change

**Before:**
```
login → issue JWT {2fa_verified: true} → done
```

**After:**
```
login → issue JWT {2fa_verified: false, exp: 5min}
      → Nginx sees false
      → redirect to /2fa/verify
      → user enters TOTP or email OTP code
      → 2FA service re-signs JWT {2fa_verified: true, exp: 8h}
      → redirect back to app
```

## 2FA Service (Go, ~300 lines)

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | /2fa/verify | Render TOTP/email OTP input page |
| POST | /2fa/verify | Verify code, re-sign JWT with `2fa_verified: true` |
| POST | /2fa/email | Send 6-digit OTP to user's email |
| GET | /2fa/setup | Show QR code for first-time TOTP enrollment |
| POST | /2fa/setup | Confirm TOTP enrollment |

### Dependencies

- **JWT signing key**: shared with all backends (env var or config file)
- **TOTP secrets**: MySQL (`security_monitoring.totp_secrets` table)
- **Email OTP**: 6-digit code stored in Redis (TTL 5min)
- **QR generation**: built-in (`otpauth://` URI → QR PNG)
- **TOTP library**: `github.com/pquerna/otp` (RFC 6238)

### MySQL Table

```sql
CREATE TABLE totp_secrets (
    user_id    VARCHAR(64) PRIMARY KEY,
    secret     VARCHAR(128) NOT NULL,       -- base32 TOTP secret
    enabled    TINYINT(1) DEFAULT 0,        -- user completed setup
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## File Layout

```
common/
├── 2fa-service/
│   ├── main.go
│   ├── totp.go            ← TOTP generate/verify
│   ├── email.go           ← SMTP email OTP sender
│   ├── jwt.go             ← decode partial JWT, re-sign full JWT
│   └── templates/
│       ├── verify.html    ← TOTP input + "send email" button
│       └── setup.html     ← QR code + confirm page
│
└── nginx/
    └── snippets/
        └── require_2fa.conf

supervisord.conf:
    [program:2fa-service]   ← new process, port 7103

Nginx routes:
    location /2fa/ {
        proxy_pass http://127.0.0.1:7103;
    }
```

## Key Design Points

1. **Backends don't change** except issuing `2fa_verified: false` in JWT after login
2. **Nginx enforces 2FA** — single `include` line per protected location
3. **Supports TOTP** (Google Authenticator, Authy, Bitwarden, 1Password) **and email OTP**
4. **First-time setup** via QR code page — user scans with authenticator app
5. **Port 7103** — follows night-watcher port convention (7100 range)
6. **Reuses existing infra** — Redis for email OTP TTL, MySQL for TOTP secrets
