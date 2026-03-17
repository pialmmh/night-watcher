# 2FA for SSH Access — Design

## Overview

Use Google Authenticator PAM module to add TOTP verification to SSH login. Users authenticate with SSH key + TOTP code. No external services needed — everything runs locally on each host via PAM.

## Flow

```
Developer runs: ssh root@10.10.195.200
         │
         ▼
┌──────────────────────────┐
│    OpenSSH Server        │
│                          │
│ AuthenticationMethods:   │
│   publickey,             │
│   keyboard-interactive   │
│                          │
│ Step 1: publickey        │
│   ← ~/.ssh/id_rsa       │
│   ✓ key accepted         │
│                          │
│ Step 2: keyboard-        │
│   interactive (PAM)      │
│         │                │
│         ▼                │
│ ┌──────────────────────┐ │
│ │ PAM stack            │ │
│ │                      │ │
│ │ pam_google_          │ │
│ │ authenticator.so     │ │
│ │       │              │ │
│ │       ▼              │ │
│ │ Prompt:              │ │
│ │ "Verification        │ │
│ │  code: "             │ │◄── User enters 6-digit
│ │       │              │ │    TOTP from phone app
│ │       ▼              │ │
│ │ Check code against   │ │
│ │ ~/.google_           │ │
│ │ authenticator         │ │
│ │ (secret + time window)│ │
│ │       │              │ │
│ │  MATCH? ──── NO ─────┼─┼──► Connection refused
│ │       │              │ │
│ │      YES             │ │
│ └───────┼──────────────┘ │
│         ▼                │
│   Shell granted          │
└──────────────────────────┘
```

## Installation (per host)

```bash
apt install libpam-google-authenticator
```

## Configuration

### /etc/pam.d/sshd

Add at the end:

```
auth required pam_google_authenticator.so nullok
```

`nullok` allows users who haven't set up TOTP yet to log in without it. Remove `nullok` to enforce TOTP for all users.

### /etc/ssh/sshd_config

```
ChallengeResponseAuthentication yes
AuthenticationMethods publickey,keyboard-interactive
UsePAM yes
```

Then restart:

```bash
systemctl restart sshd
```

## Per-User Setup (one-time)

Each user runs this on the server:

```bash
google-authenticator
```

This will:
1. Print a QR code in the terminal
2. User scans with authenticator app (Google Authenticator, Authy, Bitwarden, 1Password)
3. Generate scratch codes (emergency backup codes)
4. Write `~/.google_authenticator` file containing the TOTP secret

### Interactive prompts (recommended answers):

```
Do you want authentication tokens to be time-based? → y
Do you want me to update your ~/.google_authenticator file? → y
Do you want to disallow multiple uses of the same token? → y
Do you want to increase the time skew window? → n
Do you want to enable rate-limiting? → y
```

## Deployment Strategy

### Which servers get 2FA

| Server | 2FA? | Reason |
|--------|------|--------|
| Production hosts (dell-sms-master, dell-sms-slave, sbc1-4) | Yes | Critical infrastructure |
| Kafka/DB servers | Yes | Data access |
| Dev/staging | Optional | Lower risk |

### Automation via night-watcher deploy

The night-watcher deploy script can install and configure the PAM module on each host:

```bash
# In deploy.sh or as a separate setup script
ssh root@$HOST <<'SETUP'
apt install -y libpam-google-authenticator

# Add PAM config if not present
grep -q pam_google_authenticator /etc/pam.d/sshd || \
    echo "auth required pam_google_authenticator.so nullok" >> /etc/pam.d/sshd

# Update sshd_config
sed -i 's/^ChallengeResponseAuthentication.*/ChallengeResponseAuthentication yes/' /etc/ssh/sshd_config
sed -i 's/^#*AuthenticationMethods.*/AuthenticationMethods publickey,keyboard-interactive/' /etc/ssh/sshd_config

systemctl restart sshd
SETUP
```

User still must manually run `google-authenticator` on first use to generate their secret and scan the QR code.

## Compatibility

Works with any TOTP authenticator app:
- Google Authenticator
- Authy
- Bitwarden
- 1Password
- Microsoft Authenticator
- FreeOTP

## Key Points

1. **No external dependencies** — runs entirely via PAM, no network calls
2. **Per-user secret** stored in `~/.google_authenticator` on each server
3. **SSH key still required** — TOTP is an additional factor, not a replacement
4. **Scratch codes** provide emergency access if phone is lost
5. **Rate limiting** built into PAM module (3 attempts per 30s)
6. **`nullok` flag** allows gradual rollout — users without TOTP set up can still log in until enforced
