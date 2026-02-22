import { useState, useEffect, useCallback } from 'react';
import { Typography, Alert, Grid, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import TopIPs from '../components/TopIPs';
import DateRangePicker from '../components/DateRangePicker';
import { fetchTopIPs, fetchTopURLs } from '../api/opensearch';
import { sanitizeText } from '../utils/sanitize';

export default function Network() {
  const [timeRange, setTimeRange] = useState('now-24h');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [ips, setIps] = useState([]);
  const [urls, setUrls] = useState([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [i, u] = await Promise.all([
        fetchTopIPs(timeRange),
        fetchTopURLs(timeRange),
      ]);
      setIps(i);
      setUrls(u);
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
        Network
      </Typography>
      <DateRangePicker value={timeRange} onChange={setTimeRange} />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Grid container spacing={2}>
        <Grid item xs={12}>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Top Source IPs
          </Typography>
          <TopIPs data={ips} loading={loading} />
        </Grid>

        <Grid item xs={12}>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            URLs Triggering Alerts
          </Typography>
          {!loading && urls.length === 0 ? (
            <Paper sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary">
                No URL data in current time range
              </Typography>
            </Paper>
          ) : (
            <TableContainer component={Paper}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>URL</TableCell>
                    <TableCell align="right">Count</TableCell>
                    <TableCell>Top Rules</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {urls.map((row) => (
                    <TableRow key={row.url} hover>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: 12, maxWidth: 400, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {row.url}
                      </TableCell>
                      <TableCell align="right">{row.count}</TableCell>
                      <TableCell sx={{ fontSize: 12 }}>
                        {row.topRules?.map(r => sanitizeText(r)).join(', ') || '-'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Grid>
      </Grid>
    </>
  );
}
