import { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Paper, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, TablePagination, Collapse, IconButton, Chip,
  TextField, InputAdornment, ToggleButton, ToggleButtonGroup,
  Select, MenuItem, FormControl, InputLabel, Stack, Alert,
  CircularProgress, Card, CardContent, Grid, Tooltip, Button,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import RefreshIcon from '@mui/icons-material/Refresh';
import StorageIcon from '@mui/icons-material/Storage';
import SecurityIcon from '@mui/icons-material/Security';
import ShieldIcon from '@mui/icons-material/Shield';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SettingsIcon from '@mui/icons-material/Settings';
import BlockIcon from '@mui/icons-material/Block';
import GppGoodIcon from '@mui/icons-material/GppGood';
import LockIcon from '@mui/icons-material/Lock';
import FingerprintIcon from '@mui/icons-material/Fingerprint';
import { fetchLogs, fetchModuleSummary } from '../api/opensearch';
import DateRangePicker from '../components/DateRangePicker';
import { sanitizeText } from '../utils/sanitize';

const SEVERITY_COLORS = {
  critical: '#d32f2f', high: '#f57c00', medium: '#fbc02d', low: '#388e3c',
};

function levelToSeverity(level) {
  if (level >= 12) return 'critical';
  if (level >= 8) return 'high';
  if (level >= 5) return 'medium';
  return 'low';
}

const MODULE_META = {
  all:       { label: 'All Logs',   icon: <StorageIcon fontSize="small" /> },
  nginx:     { label: 'Web Server',  icon: <SecurityIcon fontSize="small" /> },
  waf:       { label: 'WAF',        icon: <ShieldIcon fontSize="small" /> },
  fail2ban:  { label: 'Intrusion Prevention', icon: <BlockIcon fontSize="small" /> },
  crowdsec:  { label: 'Threat Intel', icon: <GppGoodIcon fontSize="small" /> },
  watchdog:  { label: 'Watchdog',   icon: <MonitorHeartIcon fontSize="small" /> },
  auth:      { label: 'Auth/SSH',   icon: <LockIcon fontSize="small" /> },
  syscheck:  { label: 'Integrity',  icon: <FingerprintIcon fontSize="small" /> },
  system:    { label: 'System',     icon: <SettingsIcon fontSize="small" /> },
};

function LogRow({ event }) {
  const [open, setOpen] = useState(false);
  const sev = levelToSeverity(event.rule?.level || 0);
  const ts = new Date(event.timestamp).toLocaleString();

  return (
    <>
      <TableRow
        hover
        onClick={() => setOpen(!open)}
        sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
      >
        <TableCell padding="checkbox" sx={{ width: 30 }}>
          <IconButton size="small">
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell sx={{ whiteSpace: 'nowrap', fontSize: 12, width: 160 }}>{ts}</TableCell>
        <TableCell sx={{ width: 60 }}>
          <Chip
            label={event.rule?.level}
            size="small"
            sx={{
              bgcolor: SEVERITY_COLORS[sev],
              color: sev === 'medium' ? '#000' : '#fff',
              fontWeight: 600, fontSize: 11, height: 22,
            }}
          />
        </TableCell>
        <TableCell sx={{ width: 100, fontSize: 12 }}>
          {event.rule?.id || '-'}
        </TableCell>
        <TableCell sx={{ fontSize: 12, maxWidth: 300 }}>
          {sanitizeText(event.rule?.description) || '-'}
        </TableCell>
        <TableCell sx={{ fontSize: 12 }}>{event.data?.srcip || '-'}</TableCell>
        <TableCell sx={{ fontSize: 12, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {event.data?.url || '-'}
        </TableCell>
        <TableCell sx={{ fontSize: 11, color: 'text.secondary' }}>
          {event.location?.replace('/var/log/', '') || '-'}
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={8} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ p: 2 }}>
              {/* Groups + MITRE */}
              <Stack direction="row" spacing={1} mb={1} flexWrap="wrap" useFlexGap>
                {(event.rule?.groups || []).map((g) => (
                  <Chip key={g} label={sanitizeText(g)} size="small" variant="outlined" sx={{ fontSize: 11 }} />
                ))}
                {(event.rule?.mitre?.tactic || []).map((t) => (
                  <Chip key={t} label={`MITRE: ${t}`} size="small" color="error" sx={{ fontSize: 11 }} />
                ))}
              </Stack>
              {/* Full log */}
              <Box sx={{
                bgcolor: '#1e1e1e', color: '#d4d4d4', p: 1.5, borderRadius: 1,
                fontFamily: 'monospace', fontSize: 12, whiteSpace: 'pre-wrap',
                wordBreak: 'break-all', maxHeight: 300, overflow: 'auto',
              }}>
                {event._highlight ? (
                  <span dangerouslySetInnerHTML={{ __html: sanitizeText(event._highlight) }} />
                ) : (
                  sanitizeText(event.full_log) || 'No log data available'
                )}
              </Box>
              {/* Metadata row */}
              <Stack direction="row" spacing={3} mt={1} sx={{ fontSize: 11, color: 'text.secondary' }}>
                <span>Agent: {event.agent?.name || '-'}</span>
                <span>Decoder: {event.decoder?.name || '-'}</span>
                <span>Location: {event.location || '-'}</span>
                <span>ID: {event.id || '-'}</span>
              </Stack>
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

function ModuleSummaryCards({ summary, activeModule, onModuleClick }) {
  if (!summary || summary.length === 0) return null;

  const moduleCounts = { all: 0, nginx: 0, waf: 0, watchdog: 0, system: 0 };
  for (const s of summary) {
    moduleCounts[s.module] = (moduleCounts[s.module] || 0) + s.count;
    moduleCounts.all += s.count;
  }

  return (
    <Grid container spacing={1.5} mb={2}>
      {Object.entries(MODULE_META).map(([key, meta]) => (
        <Grid item xs={4} sm={2} md={4/3} key={key}>
          <Card
            variant={activeModule === key ? 'elevation' : 'outlined'}
            sx={{
              cursor: 'pointer',
              bgcolor: activeModule === key ? 'primary.main' : 'background.paper',
              color: activeModule === key ? 'primary.contrastText' : 'text.primary',
              transition: 'all 0.15s',
              '&:hover': { boxShadow: 3 },
            }}
            onClick={() => onModuleClick(key)}
          >
            <CardContent sx={{ p: '12px !important', textAlign: 'center' }}>
              <Stack direction="row" alignItems="center" justifyContent="center" spacing={0.5}>
                {meta.icon}
                <Typography variant="body2" fontWeight={600}>{meta.label}</Typography>
              </Stack>
              <Typography variant="h5" fontWeight={700} mt={0.5}>
                {(moduleCounts[key] || 0).toLocaleString()}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

export default function Logs() {
  const [timeRange, setTimeRange] = useState('now-24h');
  const [module, setModule] = useState('all');
  const [searchText, setSearchText] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [levelFilter, setLevelFilter] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(50);
  const [events, setEvents] = useState([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadSummary = useCallback(async () => {
    try {
      const s = await fetchModuleSummary(timeRange);
      setSummary(s);
    } catch (e) {
      console.error('Summary error:', e);
    }
  }, [timeRange]);

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {
        from: page * rowsPerPage,
        size: rowsPerPage,
        timeRange,
        module: module === 'all' ? null : module,
        searchText: searchText || null,
      };
      if (levelFilter) {
        const [min, max] = levelFilter.split('-').map(Number);
        params.levelMin = min;
        params.levelMax = max || null;
      }
      const result = await fetchLogs(params);
      setEvents(result.events);
      setTotal(result.total);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [timeRange, module, searchText, levelFilter, page, rowsPerPage]);

  useEffect(() => { loadSummary(); }, [loadSummary]);
  useEffect(() => { loadLogs(); }, [loadLogs]);

  const handleSearch = (e) => {
    e.preventDefault();
    setSearchText(searchInput);
    setPage(0);
  };

  const handleModuleClick = (mod) => {
    setModule(mod);
    setPage(0);
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6" fontWeight={700}>Log Explorer</Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <Tooltip title="Refresh">
            <IconButton size="small" onClick={() => { loadSummary(); loadLogs(); }}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
          <DateRangePicker value={timeRange} onChange={(v) => { setTimeRange(v); setPage(0); }} />
        </Stack>
      </Stack>

      {/* Module summary cards */}
      <ModuleSummaryCards summary={summary} activeModule={module} onModuleClick={handleModuleClick} />

      {/* Filters row */}
      <Paper variant="outlined" sx={{ p: 1.5, mb: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" useFlexGap>
          {/* Search */}
          <Box component="form" onSubmit={handleSearch} sx={{ flex: 1, minWidth: 250 }}>
            <TextField
              size="small"
              fullWidth
              placeholder="Search logs... (e.g. XSS, /etc/passwd, 403, blocked)"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment>
                ),
                endAdornment: searchText && (
                  <InputAdornment position="end">
                    <Button size="small" onClick={() => { setSearchInput(''); setSearchText(''); setPage(0); }}>
                      Clear
                    </Button>
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          {/* Level filter */}
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel>Severity</InputLabel>
            <Select
              value={levelFilter}
              label="Severity"
              onChange={(e) => { setLevelFilter(e.target.value); setPage(0); }}
            >
              <MenuItem value="">All levels</MenuItem>
              <MenuItem value="12-">Critical (12+)</MenuItem>
              <MenuItem value="8-11">High (8-11)</MenuItem>
              <MenuItem value="5-7">Medium (5-7)</MenuItem>
              <MenuItem value="3-4">Low (3-4)</MenuItem>
            </Select>
          </FormControl>
        </Stack>
      </Paper>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {/* Results info */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={0.5}>
        <Typography variant="body2" color="text.secondary">
          {loading ? 'Loading...' : `${total.toLocaleString()} logs found`}
          {searchText && <> matching &quot;<b>{searchText}</b>&quot;</>}
          {module !== 'all' && <> in <b>{MODULE_META[module]?.label}</b></>}
        </Typography>
      </Stack>

      {/* Log table */}
      <TableContainer component={Paper} variant="outlined">
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {!loading && (
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell padding="checkbox" />
                <TableCell sx={{ fontWeight: 700 }}>Time</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Lvl</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Rule</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Description</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Src IP</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>URL</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Source</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                    No logs found for the selected filters.
                  </TableCell>
                </TableRow>
              ) : (
                events.map((evt) => <LogRow key={evt.id} event={evt} />)
              )}
            </TableBody>
          </Table>
        )}
        <TablePagination
          component="div"
          count={total}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[25, 50, 100, 200]}
        />
      </TableContainer>
    </Box>
  );
}
