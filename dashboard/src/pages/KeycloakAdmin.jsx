import { useState } from 'react';
import {
  Typography, Card, CardContent, Grid, Box, Chip, Tabs, Tab, Table, TableHead,
  TableBody, TableRow, TableCell, TableContainer, TextField, Button, Alert, Divider,
} from '@mui/material';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import GroupIcon from '@mui/icons-material/Group';
import AppsIcon from '@mui/icons-material/Apps';
import SettingsIcon from '@mui/icons-material/Settings';
import SecurityIcon from '@mui/icons-material/Security';
import TimerIcon from '@mui/icons-material/Timer';
import LinkIcon from '@mui/icons-material/Link';

export default function KeycloakAdmin() {
  const [tab, setTab] = useState(0);

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Keycloak Identity</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Keycloak backend configuration for JWT-based authentication. Custom UI — Keycloak admin console not exposed.
      </Typography>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Realm Config" />
        <Tab label="Clients" />
        <Tab label="Roles & Mapping" />
        <Tab label="Integration" />
      </Tabs>

      {tab === 0 && <RealmConfig />}
      {tab === 1 && <ClientsTab />}
      {tab === 2 && <RolesTab />}
      {tab === 3 && <IntegrationTab />}
    </>
  );
}

function RealmConfig() {
  const realmSettings = [
    { label: 'Realm Name', value: 'night-watcher', icon: <VpnKeyIcon /> },
    { label: 'Login', value: 'Username / Password', icon: <SecurityIcon /> },
    { label: '2FA', value: 'Optional TOTP (planned)', icon: <SecurityIcon /> },
    { label: 'Password Policy', value: '8+ chars, 1 uppercase, 1 number', icon: <SettingsIcon /> },
    { label: 'Brute Force', value: 'Lock after 5 failed attempts for 5 min', icon: <TimerIcon /> },
    { label: 'Session TTL', value: 'Access: 5 min, Refresh: 8 hours', icon: <TimerIcon /> },
    { label: 'Token Format', value: 'JWT (RS256)', icon: <VpnKeyIcon /> },
    { label: 'Port', value: '7104 (internal, proxied at /auth/)', icon: <LinkIcon /> },
  ];

  return (
    <Grid container spacing={2}>
      <Grid item xs={12} md={6}>
        <Card>
          <CardContent sx={{ py: 2, px: 3 }}>
            <Typography variant="subtitle2" fontWeight={600} gutterBottom>Realm Settings</Typography>
            {realmSettings.map((s) => (
              <Box key={s.label} sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 0.5 }}>
                <Box sx={{ color: 'text.secondary', display: 'flex' }}>{s.icon}</Box>
                <Typography variant="body2" sx={{ minWidth: 140 }}><strong>{s.label}:</strong></Typography>
                <Typography variant="body2">{s.value}</Typography>
              </Box>
            ))}
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={12} md={6}>
        <Card>
          <CardContent sx={{ py: 2, px: 3 }}>
            <Typography variant="subtitle2" fontWeight={600} gutterBottom>Token Endpoints</Typography>
            {[
              { label: 'Token', path: '/auth/realms/night-watcher/protocol/openid-connect/token' },
              { label: 'UserInfo', path: '/auth/realms/night-watcher/protocol/openid-connect/userinfo' },
              { label: 'Logout', path: '/auth/realms/night-watcher/protocol/openid-connect/logout' },
              { label: 'Account', path: '/auth/realms/night-watcher/account' },
              { label: 'Admin API', path: '/auth/admin/realms/night-watcher/' },
            ].map((ep) => (
              <Box key={ep.label} sx={{ py: 0.5 }}>
                <Typography variant="caption" color="text.secondary">{ep.label}</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11 }}>{ep.path}</Typography>
              </Box>
            ))}
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}

function ClientsTab() {
  const clients = [
    { id: 'nw-dashboard', name: 'Night-Watcher Dashboard', type: 'Public SPA', grants: 'password, refresh_token', origins: 'https://*.telcobright.com, http://localhost:7100', status: 'active' },
    { id: 'api-gateway', name: 'API Gateway', type: 'Confidential', grants: 'client_credentials', origins: 'Internal only', status: 'planned' },
    { id: 'routesphere', name: 'Routesphere Core', type: 'Confidential', grants: 'client_credentials', origins: 'Internal only', status: 'planned' },
  ];

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        OIDC clients registered in the night-watcher realm. Each client has its own access type and grant flows.
      </Typography>
      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Client ID</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Grants</TableCell>
                <TableCell>Allowed Origins</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {clients.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{c.id}</TableCell>
                  <TableCell>{c.name}</TableCell>
                  <TableCell><Chip label={c.type} size="small" variant="outlined" sx={{ fontSize: 11 }} /></TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{c.grants}</TableCell>
                  <TableCell sx={{ fontSize: 12 }}>{c.origins}</TableCell>
                  <TableCell>
                    <Chip label={c.status} size="small" color={c.status === 'active' ? 'success' : 'default'} sx={{ fontSize: 11 }} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </>
  );
}

function RolesTab() {
  const roleMapping = [
    { role: 'admin', kcRole: 'ROLE_ADMIN', gateway: 'Allow (Super Admin)', access: 'All endpoints', color: '#d32f2f' },
    { role: 'operator', kcRole: 'ROLE_USER', gateway: 'CallingPortalUser', access: '315+ endpoints', color: '#1565c0' },
    { role: 'btrc', kcRole: 'ROLE_BTRC', gateway: 'BtrcUser', access: '11 endpoints (QoS)', color: '#7b1fa2' },
    { role: 'readonly', kcRole: 'ROLE_READONLY', gateway: 'ReadOnly', access: '24 endpoints (dashboards)', color: '#616161' },
    { role: 'reseller', kcRole: 'ROLE_RESELLER', gateway: 'Reseller', access: '90+ endpoints (partner mgmt)', color: '#2e7d32' },
    { role: 'webrtc', kcRole: 'ROLE_WEBRTC', gateway: 'WebRtc', access: '7 endpoints (contacts, calls)', color: '#e65100' },
    { role: 'smssender', kcRole: 'ROLE_SMSSENDER', gateway: 'SmsSender', access: '1 endpoint (sms/send)', color: '#00838f' },
  ];

  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Keycloak realm roles map to API Gateway policy types. The gateway reads <code>realm_access.roles</code> from the JWT.
      </Typography>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Keycloak Role</TableCell>
                <TableCell>JWT Claim</TableCell>
                <TableCell>Gateway Policy</TableCell>
                <TableCell>Access</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {roleMapping.map((r) => (
                <TableRow key={r.role} hover>
                  <TableCell>
                    <Chip label={r.role} size="small" sx={{ borderLeft: `3px solid ${r.color}`, fontSize: 12 }} />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{r.kcRole}</TableCell>
                  <TableCell>{r.gateway}</TableCell>
                  <TableCell><Typography variant="caption">{r.access}</Typography></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Alert severity="info" sx={{ mt: 2 }}>
        <strong>How it works:</strong> Keycloak issues JWT with <code>realm_access.roles: ["ROLE_USER"]</code> →
        Gateway's <code>HttpContextBuilder</code> extracts roles → <code>CallingPortalUserPolicy.match()</code> returns true →
        endpoint access check passes → request forwarded to backend service.
      </Alert>
    </>
  );
}

function IntegrationTab() {
  return (
    <>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        How to connect the API Gateway and other services to Keycloak for token validation.
      </Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ py: 2, px: 3 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>API Gateway Integration</Typography>
              <Divider sx={{ mb: 1 }} />
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                Change the token validation URL in gateway's application.properties:
              </Typography>
              <Box sx={{ bgcolor: 'grey.100', p: 1.5, borderRadius: 1, fontFamily: 'monospace', fontSize: 11 }}>
                <Typography variant="caption" color="text.secondary" display="block"># Before (custom auth service)</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11, textDecoration: 'line-through', color: 'text.disabled' }}>
                  security.token-validity-check-url=http://localhost:8080/auth/validateAccess?token=
                </Typography>
                <Box sx={{ mt: 1 }} />
                <Typography variant="caption" color="success.main" display="block"># After (Keycloak)</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11 }}>
                  security.token-validity-check-url=http://keycloak:7104/auth/realms/night-watcher/protocol/openid-connect/userinfo
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ py: 2, px: 3 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>Nginx JWT Validation</Typography>
              <Divider sx={{ mb: 1 }} />
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                Protect backend APIs at the Nginx level:
              </Typography>
              <Box sx={{ bgcolor: 'grey.100', p: 1.5, borderRadius: 1, fontFamily: 'monospace', fontSize: 11, whiteSpace: 'pre-wrap' }}>
{`location /api/ {
    auth_jwt "night-watcher";
    auth_jwt_key_file /etc/nginx/kc-pub.pem;
    proxy_pass http://backend;
}`}
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ py: 2, px: 3 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>Dashboard (React SPA)</Typography>
              <Divider sx={{ mb: 1 }} />
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                Already integrated. Auth flow in <code>src/auth/</code>:
              </Typography>
              <Box sx={{ fontSize: 12 }}>
                <Typography variant="body2">1. <code>keycloak.js</code> — login/refresh/logout API calls</Typography>
                <Typography variant="body2">2. <code>AuthContext.jsx</code> — token state + auto-refresh</Typography>
                <Typography variant="body2">3. <code>ProtectedRoute.jsx</code> — route guard</Typography>
                <Typography variant="body2">4. <code>Login.jsx</code> → Profile.jsx → UserManagement.jsx</Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ py: 2, px: 3 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>Supervisord</Typography>
              <Divider sx={{ mb: 1 }} />
              <Box sx={{ bgcolor: 'grey.100', p: 1.5, borderRadius: 1, fontFamily: 'monospace', fontSize: 11, whiteSpace: 'pre-wrap' }}>
{`[program:keycloak]
command=/opt/keycloak/bin/kc.sh start
  --http-port=7104
  --hostname-strict=false
  --proxy=edge
  --db=mysql
  --db-url=jdbc:mysql://...:3306/keycloak
priority=11
autorestart=true`}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
