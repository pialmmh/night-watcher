import { useState } from 'react';
import {
  Typography, Card, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  TextField, Box, Chip, Alert,
} from '@mui/material';

// Demo data — in production, fetched from /api/gateway/audit (reads MySQL audit_log table)
const DEMO_LOGS = [
  { id: 1, userIdentifier: 'admin@telcobright.com', action: 'POST /AUTHENTICATION/auth/', timestamp: '2026-03-17 10:23:45', response: '200', details: '114.130.145.70' },
  { id: 2, userIdentifier: 'operator@btcl.com', action: 'GET /FREESWITCHREST/partner/get-partner', timestamp: '2026-03-17 10:23:40', response: '200', details: '10.10.196.1' },
  { id: 3, userIdentifier: 'reseller@cosmocom.net', action: 'POST /SMSREST/sms/send', timestamp: '2026-03-17 10:23:38', response: '200', details: '103.48.16.22' },
  { id: 4, userIdentifier: 'unknown', action: 'GET /FREESWITCHREST/admin/DashBoard/system-info', timestamp: '2026-03-17 10:23:35', response: '403', details: '45.33.12.88' },
  { id: 5, userIdentifier: 'btrc@btrc.gov.bd', action: 'GET /FREESWITCHREST/qos-reports/call-drop', timestamp: '2026-03-17 10:23:30', response: '200', details: '202.134.12.5' },
  { id: 6, userIdentifier: 'admin@telcobright.com', action: 'POST /FREESWITCHREST/api/recordings/create', timestamp: '2026-03-17 10:23:25', response: '201', details: '114.130.145.70' },
  { id: 7, userIdentifier: 'operator@btcl.com', action: 'GET /FREESWITCHREST/ws/live-calls', timestamp: '2026-03-17 10:23:20', response: '101', details: '10.10.196.1' },
  { id: 8, userIdentifier: 'reseller@cosmocom.net', action: 'POST /AUTHENTICATION/editUser', timestamp: '2026-03-17 10:23:15', response: '403', details: '103.48.16.22' },
  { id: 9, userIdentifier: 'admin@telcobright.com', action: 'DELETE /FREESWITCHREST/api/v1/extensions/delete', timestamp: '2026-03-17 10:23:10', response: '200', details: '114.130.145.70' },
  { id: 10, userIdentifier: 'webrtc@cosmocom.net', action: 'GET /FREESWITCHREST/get-call-history', timestamp: '2026-03-17 10:23:05', response: '200', details: '103.48.16.55' },
];

function statusColor(code) {
  const c = parseInt(code);
  if (c >= 200 && c < 300) return 'success';
  if (c === 101) return 'info';
  if (c >= 400 && c < 500) return 'warning';
  if (c >= 500) return 'error';
  return 'default';
}

function methodColor(action) {
  if (action.startsWith('POST')) return '#1565c0';
  if (action.startsWith('DELETE')) return '#d32f2f';
  if (action.startsWith('PUT')) return '#e65100';
  return '#2e7d32';
}

export default function GatewayAudit() {
  const [search, setSearch] = useState('');

  const filtered = DEMO_LOGS.filter((l) =>
    !search ||
    l.userIdentifier.toLowerCase().includes(search.toLowerCase()) ||
    l.action.toLowerCase().includes(search.toLowerCase()) ||
    l.details.includes(search)
  );

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Audit Logs</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        All gateway requests logged to MySQL <code>audit_log</code> table. Showing demo data — connect to gateway MySQL for live logs.
      </Typography>

      <Alert severity="info" sx={{ mb: 2 }}>
        In production, this page reads from the gateway's <code>audit_log</code> MySQL table via <code>/api/gateway/audit</code>.
        Currently showing sample data for UI preview.
      </Alert>

      <Box sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Search by user, action, or IP..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ width: 350 }}
        />
      </Box>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Timestamp</TableCell>
                <TableCell>User</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Source IP</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((l) => (
                <TableRow key={l.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12, whiteSpace: 'nowrap' }}>{l.timestamp}</TableCell>
                  <TableCell sx={{ fontSize: 12 }}>{l.userIdentifier}</TableCell>
                  <TableCell>
                    <Typography variant="caption" sx={{ fontFamily: 'monospace', color: methodColor(l.action) }}>
                      {l.action}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip label={l.response} size="small" color={statusColor(l.response)} sx={{ fontSize: 11, height: 20, minWidth: 36 }} />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{l.details}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </>
  );
}
