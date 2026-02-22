import { useState, useEffect, useCallback } from 'react';
import { Typography, Alert, Grid } from '@mui/material';
import AlertTable from '../components/AlertTable';
import WafAttackTypes from '../components/WafEvents';
import DateRangePicker from '../components/DateRangePicker';
import { fetchWafEvents, fetchWafAttackTypes } from '../api/opensearch';

export default function Waf() {
  const [timeRange, setTimeRange] = useState('now-24h');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(50);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [data, setData] = useState({ events: [], total: 0 });
  const [attackTypes, setAttackTypes] = useState([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [evts, types] = await Promise.all([
        fetchWafEvents({ from: page * rowsPerPage, size: rowsPerPage, timeRange }),
        fetchWafAttackTypes(timeRange),
      ]);
      setData(evts);
      setAttackTypes(types);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [timeRange, page, rowsPerPage]);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>
        Web Application Firewall
      </Typography>
      <DateRangePicker value={timeRange} onChange={(v) => { setTimeRange(v); setPage(0); }} />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12}>
          <WafAttackTypes data={attackTypes} loading={loading} />
        </Grid>
      </Grid>
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>
        WAF Events
      </Typography>
      <AlertTable
        events={data.events}
        total={data.total}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(_, p) => setPage(p)}
        onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
        loading={loading}
      />
    </>
  );
}
