import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  Button, IconButton, Alert, Box, Chip, Tooltip,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import LogoutIcon from '@mui/icons-material/Logout';
import { useAuth } from '../auth/AuthContext';
import { getActiveSessions, deleteSession } from '../auth/keycloak';

export default function SessionManagement() {
  const { getToken } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) return;
      const data = await getActiveSessions(token);
      setSessions(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { load(); }, [load]);

  const handleKill = async (sessionId) => {
    try {
      const token = await getToken();
      await deleteSession(token, sessionId);
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  const formatTime = (ts) => {
    if (!ts) return '—';
    return new Date(ts).toLocaleString();
  };

  const sessionAge = (start) => {
    if (!start) return '—';
    const ms = Date.now() - start;
    const mins = Math.floor(ms / 60000);
    if (mins < 60) return `${mins}m`;
    const hours = Math.floor(mins / 60);
    return `${hours}h ${mins % 60}m`;
  };

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Active Sessions</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ display: 'flex', gap: 1, mb: 2, alignItems: 'center' }}>
        <Button size="small" startIcon={<RefreshIcon />} onClick={load}>Refresh</Button>
        <Chip label={`${sessions.length} active`} size="small" color="primary" variant="outlined" />
      </Box>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>User</TableCell>
                <TableCell>IP Address</TableCell>
                <TableCell>Started</TableCell>
                <TableCell>Last Access</TableCell>
                <TableCell>Duration</TableCell>
                <TableCell>Clients</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={7}>Loading...</TableCell></TableRow>
              ) : sessions.length === 0 ? (
                <TableRow><TableCell colSpan={7}>No active sessions</TableCell></TableRow>
              ) : sessions.map((s) => (
                <TableRow key={s.id} hover>
                  <TableCell><Typography variant="body2" fontWeight={500}>{s.username || '—'}</Typography></TableCell>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{s.ipAddress || '—'}</TableCell>
                  <TableCell sx={{ fontSize: 12 }}>{formatTime(s.start)}</TableCell>
                  <TableCell sx={{ fontSize: 12 }}>{formatTime(s.lastAccess)}</TableCell>
                  <TableCell><Chip label={sessionAge(s.start)} size="small" sx={{ fontSize: 11 }} /></TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {Object.keys(s.clients || {}).map(c => (
                        <Chip key={c} label={s.clients[c]} size="small" variant="outlined" sx={{ fontSize: 10, height: 20 }} />
                      ))}
                    </Box>
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Kill Session">
                      <IconButton size="small" color="error" onClick={() => handleKill(s.id)}>
                        <LogoutIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
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
