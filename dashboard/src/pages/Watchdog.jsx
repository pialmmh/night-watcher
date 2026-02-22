import { useState, useEffect, useCallback } from 'react';
import { Typography, Alert, Grid, Paper } from '@mui/material';
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid,
} from 'recharts';
import dayjs from 'dayjs';
import WatchdogStatus from '../components/WatchdogStatus';
import AlertTable from '../components/AlertTable';
import DateRangePicker from '../components/DateRangePicker';
import { fetchWatchdogStatus, fetchWatchdogEvents } from '../api/opensearch';
import { sanitizeText } from '../utils/sanitize';

export default function Watchdog() {
  const [timeRange, setTimeRange] = useState('now-24h');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(50);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState([]);
  const [events, setEvents] = useState({ events: [], total: 0 });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, e] = await Promise.all([
        fetchWatchdogStatus('now-1h'),
        fetchWatchdogEvents({ from: page * rowsPerPage, size: rowsPerPage, timeRange }),
      ]);
      setStatus(s);
      setEvents(e);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [timeRange, page, rowsPerPage]);

  useEffect(() => { load(); }, [load]);

  // Build failover timeline from events
  const failovers = (events.events || [])
    .filter((e) => String(e.rule?.id) === '100701')
    .map((e) => ({
      time: dayjs(e.timestamp).format('MM-DD HH:mm'),
      ts: e.timestamp,
      description: sanitizeText(e.rule?.description) || 'Failover',
    }));

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>
        Watchdog (Backend Health)
      </Typography>
      <DateRangePicker value={timeRange} onChange={(v) => { setTimeRange(v); setPage(0); }} />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>
        Current Status (last 1h)
      </Typography>
      <WatchdogStatus data={status} loading={loading} />

      {failovers.length > 0 && (
        <Paper sx={{ p: 2, mt: 2 }}>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Failover Events Timeline
          </Typography>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={failovers}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="time" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey={() => 1} name="Failover" fill="#d32f2f" />
            </BarChart>
          </ResponsiveContainer>
        </Paper>
      )}

      <Typography variant="subtitle1" fontWeight={600} sx={{ mt: 2, mb: 1 }}>
        All Watchdog Events
      </Typography>
      <AlertTable
        events={events.events}
        total={events.total}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(_, p) => setPage(p)}
        onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        loading={loading}
      />
    </>
  );
}
