import { useState, useEffect, useCallback } from 'react';
import { Typography, Alert, Box, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import AlertTable from '../components/AlertTable';
import DateRangePicker from '../components/DateRangePicker';
import { fetchSecurityEvents } from '../api/opensearch';

const LEVEL_OPTIONS = [
  { label: 'All Levels', value: null },
  { label: 'Level 5+', value: 5 },
  { label: 'Level 8+ (High)', value: 8 },
  { label: 'Level 12+ (Critical)', value: 12 },
];

const GROUP_OPTIONS = [
  { label: 'All Groups', value: null },
  { label: 'WAF', value: 'modsecurity' },
  { label: 'Web', value: 'web' },
  { label: 'XSS', value: 'xss' },
  { label: 'SQL Injection', value: 'sql_injection' },
  { label: 'Scanner', value: 'web-scan' },
];

export default function SecurityEvents() {
  const [timeRange, setTimeRange] = useState('now-24h');
  const [levelFilter, setLevelFilter] = useState(null);
  const [groupFilter, setGroupFilter] = useState(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(50);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [data, setData] = useState({ events: [], total: 0 });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchSecurityEvents({
        from: page * rowsPerPage,
        size: rowsPerPage,
        timeRange,
        levelFilter,
        groupFilter,
      });
      setData(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [timeRange, levelFilter, groupFilter, page, rowsPerPage]);

  useEffect(() => { load(); }, [load]);

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>
        Security Events
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>Severity</InputLabel>
          <Select
            value={levelFilter ?? ''}
            label="Severity"
            onChange={(e) => { setLevelFilter(e.target.value || null); setPage(0); }}
          >
            {LEVEL_OPTIONS.map((o) => (
              <MenuItem key={o.label} value={o.value ?? ''}>{o.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>Group</InputLabel>
          <Select
            value={groupFilter ?? ''}
            label="Group"
            onChange={(e) => { setGroupFilter(e.target.value || null); setPage(0); }}
          >
            {GROUP_OPTIONS.map((o) => (
              <MenuItem key={o.label} value={o.value ?? ''}>{o.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <Box sx={{ flexGrow: 1 }} />
        <DateRangePicker value={timeRange} onChange={(v) => { setTimeRange(v); setPage(0); }} />
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
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
