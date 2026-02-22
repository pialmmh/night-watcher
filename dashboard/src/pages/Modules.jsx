import { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Paper, Grid, Card, CardContent, Chip, Stack,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Alert, CircularProgress, IconButton, Tooltip, Divider, LinearProgress,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import ShieldIcon from '@mui/icons-material/Shield';
import SecurityIcon from '@mui/icons-material/Security';
import BlockIcon from '@mui/icons-material/Block';
import GppGoodIcon from '@mui/icons-material/GppGood';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import StorageIcon from '@mui/icons-material/Storage';
import LanguageIcon from '@mui/icons-material/Language';
import LockIcon from '@mui/icons-material/Lock';

async function fetchModuleStatus() {
  const res = await fetch('/api/status/');
  if (!res.ok) throw new Error('Status API error');
  return res.json();
}

function ProcessCard({ name, info }) {
  const running = info.status === 'RUNNING';
  const uptime = info.detail?.replace('pid ', '').replace(/,\s*/, ' | ') || '';
  return (
    <Card variant="outlined" sx={{ borderLeft: 4, borderLeftColor: running ? 'success.main' : 'error.main' }}>
      <CardContent sx={{ p: '10px 14px !important' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Stack direction="row" alignItems="center" spacing={1}>
            {running ? <CheckCircleIcon color="success" sx={{ fontSize: 18 }} /> : <ErrorIcon color="error" sx={{ fontSize: 18 }} />}
            <Typography variant="body2" fontWeight={600}>{name}</Typography>
          </Stack>
          <Chip label={info.status} size="small" color={running ? 'success' : 'error'} sx={{ fontSize: 11, height: 22 }} />
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>{uptime}</Typography>
      </CardContent>
    </Card>
  );
}

function Fail2BanSection({ data }) {
  if (!data) return null;
  const totalBanned = data.jails.reduce((s, j) => s + j.banned, 0);
  const totalHistoric = data.jails.reduce((s, j) => s + j.total_banned, 0);
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={1.5}>
        <BlockIcon color="warning" />
        <Typography variant="subtitle1" fontWeight={700}>Intrusion Prevention</Typography>
        <Chip label={`${data.total_jails} jails`} size="small" variant="outlined" />
        <Chip label={`${totalBanned} currently banned`} size="small" color={totalBanned > 0 ? 'error' : 'success'} />
      </Stack>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell sx={{ fontWeight: 700 }}>Jail</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="center">Currently Failed</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="center">Currently Banned</TableCell>
              <TableCell sx={{ fontWeight: 700 }} align="center">Total Banned</TableCell>
              <TableCell sx={{ fontWeight: 700 }}>Banned IPs</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.jails.map((j) => (
              <TableRow key={j.name} hover>
                <TableCell>
                  <Stack direction="row" alignItems="center" spacing={0.5}>
                    <LockIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                    <Typography variant="body2" fontWeight={500}>{j.name}</Typography>
                  </Stack>
                </TableCell>
                <TableCell align="center">
                  <Chip label={j.failed} size="small" variant="outlined" color={j.failed > 0 ? 'warning' : 'default'} sx={{ minWidth: 40 }} />
                </TableCell>
                <TableCell align="center">
                  <Chip label={j.banned} size="small" color={j.banned > 0 ? 'error' : 'success'} sx={{ minWidth: 40, fontWeight: 700 }} />
                </TableCell>
                <TableCell align="center">{j.total_banned}</TableCell>
                <TableCell>
                  {j.banned_ips.length > 0 ? j.banned_ips.map((ip) => (
                    <Chip key={ip} label={ip} size="small" sx={{ mr: 0.5, fontSize: 11 }} color="error" variant="outlined" />
                  )) : <Typography variant="caption" color="text.secondary">none</Typography>}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {totalHistoric === 0 && (
        <Alert severity="info" sx={{ mt: 1 }}>
          No bans triggered yet. The intrusion prevention system is monitoring web access logs, error logs, WAF audit logs, and SSH auth logs. Bans will appear here when attack thresholds are reached.
        </Alert>
      )}
    </Paper>
  );
}

function CrowdSecSection({ data }) {
  if (!data) return null;
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={1.5}>
        <GppGoodIcon color="primary" />
        <Typography variant="subtitle1" fontWeight={700}>Threat Intelligence</Typography>
        <Chip label={`${data.decision_count} active decisions`} size="small" color={data.decision_count > 0 ? 'warning' : 'success'} />
        <Chip label={`${data.alert_count} alerts`} size="small" variant="outlined" />
      </Stack>

      {data.decision_count > 0 ? (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>IP/Range</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Reason</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Duration</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Type</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.decisions.map((d, i) => (
                <TableRow key={i} hover>
                  <TableCell><Chip label={d.value || d.scope} size="small" color="error" variant="outlined" /></TableCell>
                  <TableCell>{d.scenario || '-'}</TableCell>
                  <TableCell>{d.duration || '-'}</TableCell>
                  <TableCell><Chip label={d.type || 'ban'} size="small" /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      ) : (
        <Alert severity="info">
          No active threat decisions. The threat intelligence engine is running and connected to the community blocklist. Decisions (bans/captchas) will appear here when threats are detected via behavior analysis.
        </Alert>
      )}

      {data.alert_count > 0 && (
        <Box mt={2}>
          <Typography variant="body2" fontWeight={600} mb={1}>Recent Alerts</Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Scenario</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Source</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Events</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.alerts.slice(0, 10).map((a, i) => (
                  <TableRow key={i} hover>
                    <TableCell>{a.scenario || '-'}</TableCell>
                    <TableCell>{a.source?.ip || a.source?.value || '-'}</TableCell>
                    <TableCell>{a.events_count || '-'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}

      {/* Bouncers */}
      {data.bouncers && data.bouncers.length > 0 && (
        <Box mt={2}>
          <Typography variant="body2" fontWeight={600} mb={1}>Registered Bouncers</Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {data.bouncers.map((b, i) => (
              <Chip
                key={i}
                label={b.name}
                size="small"
                color={b.revoked ? 'error' : 'success'}
                variant="outlined"
                icon={<CheckCircleIcon />}
              />
            ))}
          </Stack>
        </Box>
      )}

      {/* Collections */}
      {data.collections?.collections && data.collections.collections.length > 0 && (
        <Box mt={2}>
          <Typography variant="body2" fontWeight={600} mb={1}>Installed Collections</Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Collection</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Version</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Description</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.collections.collections.map((c, i) => (
                  <TableRow key={i} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={500} sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                        {c.name}
                      </Typography>
                    </TableCell>
                    <TableCell>{c.local_version || '-'}</TableCell>
                    <TableCell sx={{ fontSize: 12 }}>{c.description || '-'}</TableCell>
                    <TableCell>
                      <Chip
                        label={c.status || 'unknown'}
                        size="small"
                        color={c.status === 'enabled' ? 'success' : 'default'}
                        sx={{ fontSize: 11, height: 22 }}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}
    </Paper>
  );
}

function NginxWafSection({ data }) {
  if (!data) return null;
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={1.5}>
        <LanguageIcon color="info" />
        <Typography variant="subtitle1" fontWeight={700}>Web Application Firewall</Typography>
      </Stack>
      <Grid container spacing={2}>
        <Grid item xs={4}>
          <Card variant="outlined" sx={{ textAlign: 'center', p: 1 }}>
            <Typography variant="caption" color="text.secondary">Access Log Lines</Typography>
            <Typography variant="h5" fontWeight={700}>{data.access_log_lines.toLocaleString()}</Typography>
          </Card>
        </Grid>
        <Grid item xs={4}>
          <Card variant="outlined" sx={{ textAlign: 'center', p: 1 }}>
            <Typography variant="caption" color="text.secondary">Error Log Lines</Typography>
            <Typography variant="h5" fontWeight={700} color={data.error_log_lines > 0 ? 'warning.main' : 'success.main'}>
              {data.error_log_lines.toLocaleString()}
            </Typography>
          </Card>
        </Grid>
        <Grid item xs={4}>
          <Card variant="outlined" sx={{ textAlign: 'center', p: 1 }}>
            <Typography variant="caption" color="text.secondary">WAF Events</Typography>
            <Typography variant="h5" fontWeight={700} color={data.modsec_log_lines > 0 ? 'error.main' : 'success.main'}>
              {data.modsec_log_lines.toLocaleString()}
            </Typography>
          </Card>
        </Grid>
      </Grid>
    </Paper>
  );
}

function WazuhSection({ data }) {
  if (!data) return null;
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={1.5}>
        <StorageIcon color="secondary" />
        <Typography variant="subtitle1" fontWeight={700}>Security Monitoring (SIEM)</Typography>
      </Stack>
      <Grid container spacing={2}>
        <Grid item xs={6}>
          <Card variant="outlined" sx={{ textAlign: 'center', p: 1 }}>
            <Typography variant="caption" color="text.secondary">Running Daemons</Typography>
            <Typography variant="h5" fontWeight={700} color="success.main">{data.running_daemons}</Typography>
          </Card>
        </Grid>
        <Grid item xs={6}>
          <Card variant="outlined" sx={{ textAlign: 'center', p: 1 }}>
            <Typography variant="caption" color="text.secondary">Total Alerts Generated</Typography>
            <Typography variant="h5" fontWeight={700}>{data.total_alerts.toLocaleString()}</Typography>
          </Card>
        </Grid>
      </Grid>
    </Paper>
  );
}

export default function Modules() {
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchModuleStatus();
      setStatus(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, [load]);

  if (loading && !status) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}><CircularProgress /></Box>;
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6" fontWeight={700}>Security Modules</Typography>
        <Tooltip title="Refresh (auto-refreshes every 30s)">
          <IconButton size="small" onClick={load} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>
      {loading && <LinearProgress sx={{ mb: 1 }} />}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {status && (
        <Stack spacing={2.5}>
          {/* Process Status Grid */}
          <Box>
            <Typography variant="subtitle2" color="text.secondary" mb={1}>Process Status (Supervisord)</Typography>
            <Grid container spacing={1.5}>
              {Object.entries(status.processes || {}).map(([name, info]) => (
                <Grid item xs={6} sm={4} md={3} key={name}>
                  <ProcessCard name={name} info={info} />
                </Grid>
              ))}
            </Grid>
          </Box>

          <Divider />

          {/* Intrusion Prevention */}
          <Fail2BanSection data={status.fail2ban} />

          {/* Threat Intelligence */}
          <CrowdSecSection data={status.crowdsec} />

          {/* Web Application Firewall */}
          <NginxWafSection data={status.nginx} />

          {/* Security Monitoring */}
          <WazuhSection data={status.wazuh} />
        </Stack>
      )}
    </Box>
  );
}
