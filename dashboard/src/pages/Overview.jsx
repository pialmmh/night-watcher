import { useState, useEffect, useCallback } from 'react';
import { Grid, Typography, Alert } from '@mui/material';
import SeverityCards from '../components/SeverityCards';
import AlertTrend from '../components/AlertTrend';
import TopRules from '../components/TopRules';
import MitreTags from '../components/MitreTags';
import DateRangePicker from '../components/DateRangePicker';
import { fetchSeverityCounts, fetchAlertTrend, fetchTopRules, fetchMitreTactics } from '../api/opensearch';

export default function Overview() {
  const [timeRange, setTimeRange] = useState('now-24h');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [counts, setCounts] = useState(null);
  const [trend, setTrend] = useState([]);
  const [rules, setRules] = useState([]);
  const [mitre, setMitre] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [c, t, r, m] = await Promise.all([
        fetchSeverityCounts(timeRange),
        fetchAlertTrend(timeRange),
        fetchTopRules(timeRange),
        fetchMitreTactics(timeRange),
      ]);
      setCounts(c);
      setTrend(t);
      setRules(r);
      setMitre(m);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [timeRange]);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>
        Security Overview
      </Typography>
      <DateRangePicker value={timeRange} onChange={setTimeRange} />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <SeverityCards counts={counts} loading={loading} />
      <Grid container spacing={2} sx={{ mt: 1 }}>
        <Grid item xs={12} lg={7}>
          <AlertTrend data={trend} loading={loading} />
        </Grid>
        <Grid item xs={12} lg={5}>
          <MitreTags data={mitre} loading={loading} />
        </Grid>
        <Grid item xs={12}>
          <TopRules data={rules} loading={loading} />
        </Grid>
      </Grid>
    </>
  );
}
