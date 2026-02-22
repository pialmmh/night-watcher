import { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Paper, Grid, Card, CardContent, Chip, Stack,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Alert, IconButton, Tooltip, LinearProgress, Skeleton,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';
import DnsIcon from '@mui/icons-material/Dns';
import FavoriteIcon from '@mui/icons-material/Favorite';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import SettingsEthernetIcon from '@mui/icons-material/SettingsEthernet';

// Node endpoints — each hactl instance has its own API port
const NODE_ENDPOINTS = [
  { id: 'node1', url: '/api/hactl/node1/status' },
  { id: 'node2', url: '/api/hactl/node2/status' },
  { id: 'node3', url: '/api/hactl/node3/status' },
];

const STATE_CONFIG = {
  LEADER:   { color: 'success', label: 'Leader',       icon: <StarIcon sx={{ fontSize: 18 }} /> },
  FOLLOWER: { color: 'info',    label: 'Follower',     icon: <StarBorderIcon sx={{ fontSize: 18 }} /> },
  INIT:     { color: 'warning', label: 'Initializing', icon: <AccessTimeIcon sx={{ fontSize: 18 }} /> },
  STOPPING: { color: 'error',   label: 'Stopping',     icon: <ErrorIcon sx={{ fontSize: 18 }} /> },
};

const HEALTH_CONFIG = {
  HEALTHY:   { color: 'success.main', icon: <CheckCircleIcon color="success" sx={{ fontSize: 18 }} />, chipColor: 'success' },
  DEGRADED:  { color: 'warning.main', icon: <WarningIcon color="warning" sx={{ fontSize: 18 }} />,     chipColor: 'warning' },
  UNHEALTHY: { color: 'error.main',   icon: <ErrorIcon color="error" sx={{ fontSize: 18 }} />,         chipColor: 'error' },
  UNKNOWN:   { color: 'grey.500',     icon: <WarningIcon color="disabled" sx={{ fontSize: 18 }} />,    chipColor: 'default' },
};

const RESOURCE_STATE_CONFIG = {
  ACTIVE:  { color: 'success', label: 'Active' },
  STANDBY: { color: 'info',    label: 'Standby' },
  STOPPED: { color: 'default', label: 'Stopped' },
  UNKNOWN: { color: 'warning', label: 'Unknown' },
};

// ── Cluster Overview — shows all nodes ─────────────────────────────────────

function ClusterOverview({ nodes }) {
  const leader = nodes.find(n => n.data?.state === 'LEADER');
  const leaderName = leader?.data?.leader || leader?.data?.nodeId || 'No leader';
  const totalNodes = nodes.length;
  const onlineNodes = nodes.filter(n => n.data && !n.error).length;
  const clusterHealthy = onlineNodes === totalNodes && leader;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={2}>
        <DeviceHubIcon color="primary" />
        <Typography variant="subtitle1" fontWeight={700}>Cluster Overview</Typography>
        <Chip
          label={clusterHealthy ? 'Healthy' : onlineNodes > 0 ? 'Degraded' : 'Down'}
          color={clusterHealthy ? 'success' : onlineNodes > 0 ? 'warning' : 'error'}
          size="small"
          sx={{ fontSize: 11, height: 22, fontWeight: 700 }}
        />
      </Stack>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        {/* Leader Card */}
        <Grid item xs={12} sm={6} md={4}>
          <Card variant="outlined" sx={{ borderLeft: 4, borderLeftColor: leader ? 'success.main' : 'warning.main', height: '100%' }}>
            <CardContent sx={{ p: '12px 14px !important' }}>
              <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
                <StarIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="caption" color="text.secondary">Current Leader</Typography>
              </Stack>
              <Typography variant="h6" fontWeight={700}>{leaderName}</Typography>
              {!leader && <Chip label="Election pending" color="warning" size="small" variant="outlined" sx={{ mt: 0.5, fontSize: 11 }} />}
            </CardContent>
          </Card>
        </Grid>

        {/* Nodes Online Card */}
        <Grid item xs={12} sm={6} md={4}>
          <Card variant="outlined" sx={{ borderLeft: 4, borderLeftColor: onlineNodes === totalNodes ? 'success.main' : 'warning.main', height: '100%' }}>
            <CardContent sx={{ p: '12px 14px !important' }}>
              <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
                <DnsIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="caption" color="text.secondary">Nodes</Typography>
              </Stack>
              <Typography variant="h6" fontWeight={700}>{onlineNodes}/{totalNodes} online</Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* Consul Card */}
        <Grid item xs={12} sm={6} md={4}>
          <Card variant="outlined" sx={{ borderLeft: 4, borderLeftColor: 'primary.main', height: '100%' }}>
            <CardContent sx={{ p: '12px 14px !important' }}>
              <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
                <SettingsEthernetIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="caption" color="text.secondary">Cluster</Typography>
              </Stack>
              <Typography variant="h6" fontWeight={700}>test-ha</Typography>
              <Typography variant="caption" color="text.secondary">3-node Consul cluster</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Paper>
  );
}

// ── Node Card — individual node status ──────────────────────────────────────

function NodeCard({ nodeInfo }) {
  const { id, data, error } = nodeInfo;

  if (error || !data) {
    return (
      <Card variant="outlined" sx={{ borderLeft: 4, borderLeftColor: 'error.main' }}>
        <CardContent sx={{ p: '12px 14px !important' }}>
          <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
            <ErrorIcon color="error" sx={{ fontSize: 18 }} />
            <Typography variant="body2" fontWeight={600}>{id}</Typography>
            <Chip label="Unreachable" color="error" size="small" sx={{ fontSize: 11, height: 22 }} />
          </Stack>
          <Typography variant="caption" color="error">{error || 'No data'}</Typography>
        </CardContent>
      </Card>
    );
  }

  const stateConf = STATE_CONFIG[data.state] || STATE_CONFIG.INIT;
  const isLeader = data.state === 'LEADER';

  return (
    <Card variant="outlined" sx={{ borderLeft: 4, borderLeftColor: `${stateConf.color}.main` }}>
      <CardContent sx={{ p: '12px 14px !important' }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
          <Stack direction="row" alignItems="center" spacing={1}>
            {stateConf.icon}
            <Typography variant="body1" fontWeight={700}>{data.nodeId}</Typography>
            <Chip
              label={stateConf.label}
              color={stateConf.color}
              size="small"
              sx={{ fontSize: 11, height: 22, fontWeight: 700 }}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">{data.uptime}</Typography>
        </Stack>

        {isLeader && (
          <Alert severity="success" icon={<StarIcon />} sx={{ py: 0, mb: 1, '& .MuiAlert-message': { py: 0.5 } }}>
            Active leader — managing resource groups
          </Alert>
        )}
        {data.state === 'FOLLOWER' && (
          <Alert severity="info" icon={<SwapHorizIcon />} sx={{ py: 0, mb: 1, '& .MuiAlert-message': { py: 0.5 } }}>
            Standby — leader: <strong>{data.leader || 'unknown'}</strong>
          </Alert>
        )}

        {/* Resource groups for this node */}
        {(data.groups || []).map(group => (
          <ResourceGroupCompact key={group.id} group={group} isLeader={isLeader} />
        ))}
      </CardContent>
    </Card>
  );
}

// ── Compact Resource Group — shown inside each node card ────────────────────

function ResourceGroupCompact({ group, isLeader }) {
  const resources = group.resources || [];
  const checks = group.checks || [];

  return (
    <Box sx={{ mt: 1 }}>
      <Stack direction="row" alignItems="center" spacing={1} mb={0.5}>
        <FavoriteIcon sx={{ fontSize: 16 }} color={isLeader ? 'success' : 'disabled'} />
        <Typography variant="body2" fontWeight={600}>{group.id}</Typography>
      </Stack>

      {/* Resources */}
      {resources.map(res => {
        const healthConf = HEALTH_CONFIG[res.health] || HEALTH_CONFIG.UNKNOWN;
        const stateConf = RESOURCE_STATE_CONFIG[res.state] || RESOURCE_STATE_CONFIG.UNKNOWN;
        return (
          <Stack key={res.id} direction="row" alignItems="center" spacing={1} sx={{ ml: 2, mb: 0.3 }}>
            {healthConf.icon}
            <Typography variant="caption" fontWeight={500}>{res.id}</Typography>
            <Chip label={res.type} size="small" variant="outlined" sx={{ fontSize: 10, height: 18 }} />
            <Chip label={stateConf.label} size="small" color={stateConf.color} sx={{ fontSize: 10, height: 18 }} />
            {res.reason && (
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', fontSize: 10 }}>
                {res.reason}
              </Typography>
            )}
          </Stack>
        );
      })}

      {/* Health Checks */}
      {checks.length > 0 && (
        <TableContainer sx={{ ml: 2, mt: 0.5 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700, py: 0.3, fontSize: 11, width: 30 }}></TableCell>
                <TableCell sx={{ fontWeight: 700, py: 0.3, fontSize: 11 }}>Check</TableCell>
                <TableCell sx={{ fontWeight: 700, py: 0.3, fontSize: 11 }}>Output</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {checks.map(chk => (
                <TableRow key={chk.name} hover>
                  <TableCell sx={{ py: 0.3 }}>
                    {chk.passed
                      ? <CheckCircleIcon color="success" sx={{ fontSize: 16 }} />
                      : <ErrorIcon color="error" sx={{ fontSize: 16 }} />
                    }
                  </TableCell>
                  <TableCell sx={{ py: 0.3 }}>
                    <Typography variant="caption" fontWeight={500}>{chk.name}</Typography>
                  </TableCell>
                  <TableCell sx={{ py: 0.3 }}>
                    <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', fontSize: 10 }}>
                      {chk.output || '—'}
                    </Typography>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}

// ── Loading Skeleton ────────────────────────────────────────────────────────

function LoadingSkeleton() {
  return (
    <Stack spacing={2.5}>
      <Skeleton variant="rectangular" height={140} sx={{ borderRadius: 1 }} />
      <Grid container spacing={2}>
        {[1, 2, 3].map(i => (
          <Grid item xs={12} md={4} key={i}>
            <Skeleton variant="rectangular" height={200} sx={{ borderRadius: 1 }} />
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}

// ── Main Component ──────────────────────────────────────────────────────────

export default function HaCluster() {
  const [nodes, setNodes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [lastUpdate, setLastUpdate] = useState(null);

  const fetchAllNodes = useCallback(async () => {
    setLoading(true);
    const results = await Promise.allSettled(
      NODE_ENDPOINTS.map(async (ep) => {
        const res = await fetch(ep.url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        return { id: ep.id, data, error: null };
      })
    );

    const nodeStates = results.map((result, i) => {
      if (result.status === 'fulfilled') {
        return result.value;
      }
      return { id: NODE_ENDPOINTS[i].id, data: null, error: 'Unreachable' };
    });

    setNodes(nodeStates);
    setLastUpdate(new Date());
    setLoading(false);
  }, []);

  useEffect(() => { fetchAllNodes(); }, [fetchAllNodes]);

  // Auto-refresh every 10 seconds
  useEffect(() => {
    const interval = setInterval(fetchAllNodes, 10000);
    return () => clearInterval(interval);
  }, [fetchAllNodes]);

  const anyData = nodes.some(n => n.data);

  if (loading && !anyData) {
    return (
      <Box>
        <Typography variant="h6" fontWeight={700} mb={2}>HA Cluster</Typography>
        <LoadingSkeleton />
      </Box>
    );
  }

  if (!anyData) {
    return (
      <Box>
        <Typography variant="h6" fontWeight={700} mb={2}>HA Cluster</Typography>
        <Alert severity="warning" icon={<DeviceHubIcon />}>
          No HA controller nodes reachable — are the <code>hactl</code> instances running?
        </Alert>
        <Alert severity="info" sx={{ mt: 1 }}>
          The HA controller manages resource failover across cluster nodes via Consul.
          Enable it by setting <code>HACTL_ENABLED=true</code> in your tenant config.
        </Alert>
      </Box>
    );
  }

  return (
    <Box>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="h6" fontWeight={700}>HA Cluster</Typography>
          {lastUpdate && (
            <Typography variant="caption" color="text.secondary">
              Updated {lastUpdate.toLocaleTimeString()}
            </Typography>
          )}
        </Stack>
        <Tooltip title="Refresh (auto-refreshes every 10s)">
          <IconButton size="small" onClick={fetchAllNodes} disabled={loading}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>
      {loading && <LinearProgress sx={{ mb: 1 }} />}

      <Stack spacing={2.5}>
        {/* Cluster Overview */}
        <ClusterOverview nodes={nodes} />

        {/* Node Cards */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction="row" alignItems="center" spacing={1} mb={2}>
            <DnsIcon color="primary" />
            <Typography variant="subtitle1" fontWeight={700}>Node Status</Typography>
          </Stack>
          <Grid container spacing={2}>
            {nodes.map(nodeInfo => (
              <Grid item xs={12} md={4} key={nodeInfo.id}>
                <NodeCard nodeInfo={nodeInfo} />
              </Grid>
            ))}
          </Grid>
        </Paper>
      </Stack>
    </Box>
  );
}
