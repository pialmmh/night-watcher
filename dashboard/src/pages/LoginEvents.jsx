import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  Button, TextField, Alert, Box, Chip, Tabs, Tab,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useAuth } from '../auth/AuthContext';
import { getLoginEvents, getAdminEvents } from '../auth/keycloak';

function eventColor(type) {
  if (type === 'LOGIN') return 'success';
  if (type === 'LOGIN_ERROR') return 'error';
  if (type === 'LOGOUT') return 'info';
  if (type === 'REGISTER') return 'primary';
  if (type?.includes('ERROR')) return 'error';
  return 'default';
}

export default function LoginEvents() {
  const { getToken } = useAuth();
  const [tab, setTab] = useState(0);
  const [loginEvents, setLoginEvents] = useState([]);
  const [adminEvents, setAdminEvents] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) return;
      const [le, ae] = await Promise.all([
        getLoginEvents(token, { max: 100 }),
        getAdminEvents(token, { max: 100 }),
      ]);
      setLoginEvents(le);
      setAdminEvents(ae);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { load(); }, [load]);

  const formatTime = (ts) => {
    if (!ts) return '—';
    return new Date(ts).toLocaleString();
  };

  const filteredLogin = loginEvents.filter(e =>
    !search ||
    (e.userId || '').toLowerCase().includes(search.toLowerCase()) ||
    (e.type || '').toLowerCase().includes(search.toLowerCase()) ||
    (e.ipAddress || '').includes(search) ||
    JSON.stringify(e.details || {}).toLowerCase().includes(search.toLowerCase())
  );

  const filteredAdmin = adminEvents.filter(e =>
    !search ||
    (e.operationType || '').toLowerCase().includes(search.toLowerCase()) ||
    (e.resourcePath || '').toLowerCase().includes(search.toLowerCase()) ||
    JSON.stringify(e.authDetails || {}).toLowerCase().includes(search.toLowerCase())
  );

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Events</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label={`Login Events (${loginEvents.length})`} />
        <Tab label={`Admin Events (${adminEvents.length})`} />
      </Tabs>

      <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
        <TextField size="small" placeholder="Search events..." value={search}
          onChange={(e) => setSearch(e.target.value)} sx={{ width: 300 }} />
        <Button size="small" startIcon={<RefreshIcon />} onClick={load}>Refresh</Button>
      </Box>

      {tab === 0 && (
        <Card>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>User</TableCell>
                  <TableCell>IP Address</TableCell>
                  <TableCell>Client</TableCell>
                  <TableCell>Details</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow><TableCell colSpan={6}>Loading...</TableCell></TableRow>
                ) : filteredLogin.length === 0 ? (
                  <TableRow><TableCell colSpan={6}>No events found. Enable events in Keycloak realm settings.</TableCell></TableRow>
                ) : filteredLogin.map((e, i) => (
                  <TableRow key={i} hover>
                    <TableCell sx={{ fontSize: 12, whiteSpace: 'nowrap' }}>{formatTime(e.time)}</TableCell>
                    <TableCell><Chip label={e.type} size="small" color={eventColor(e.type)} sx={{ fontSize: 10, height: 20 }} /></TableCell>
                    <TableCell sx={{ fontSize: 12 }}>{e.details?.username || e.userId || '—'}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{e.ipAddress || '—'}</TableCell>
                    <TableCell sx={{ fontSize: 12 }}>{e.clientId || '—'}</TableCell>
                    <TableCell sx={{ fontSize: 11 }}>
                      {e.error && <Chip label={e.error} size="small" color="error" variant="outlined" sx={{ fontSize: 10 }} />}
                      {e.details?.grant_type && <Chip label={e.details.grant_type} size="small" variant="outlined" sx={{ fontSize: 10, ml: 0.5 }} />}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}

      {tab === 1 && (
        <Card>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Operation</TableCell>
                  <TableCell>Resource</TableCell>
                  <TableCell>Admin</TableCell>
                  <TableCell>IP Address</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {loading ? (
                  <TableRow><TableCell colSpan={5}>Loading...</TableCell></TableRow>
                ) : filteredAdmin.length === 0 ? (
                  <TableRow><TableCell colSpan={5}>No admin events found. Enable admin events in Keycloak realm settings.</TableCell></TableRow>
                ) : filteredAdmin.map((e, i) => (
                  <TableRow key={i} hover>
                    <TableCell sx={{ fontSize: 12, whiteSpace: 'nowrap' }}>{formatTime(e.time)}</TableCell>
                    <TableCell>
                      <Chip label={e.operationType} size="small" color={e.operationType === 'CREATE' ? 'success' : e.operationType === 'DELETE' ? 'error' : 'default'}
                        sx={{ fontSize: 10, height: 20 }} />
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{e.resourcePath || '—'}</TableCell>
                    <TableCell sx={{ fontSize: 12 }}>{e.authDetails?.username || '—'}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{e.authDetails?.ipAddress || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}
    </>
  );
}
