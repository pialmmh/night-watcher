import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Typography, Chip,
} from '@mui/material';
import { sanitizeText } from '../utils/sanitize';

function levelChip(level) {
  if (level >= 12) return <Chip label="Crit" size="small" color="error" />;
  if (level >= 8) return <Chip label="High" size="small" sx={{ bgcolor: '#f57c00', color: '#fff' }} />;
  if (level >= 5) return <Chip label="Med" size="small" sx={{ bgcolor: '#fbc02d', color: '#000' }} />;
  return <Chip label="Low" size="small" color="success" />;
}

export default function TopIPs({ data, loading }) {
  if (!loading && (!data || data.length === 0)) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="body2" color="text.secondary">
          No source IP data in current time range
        </Typography>
      </Paper>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Source IP</TableCell>
            <TableCell align="right">Count</TableCell>
            <TableCell>Max Severity</TableCell>
            <TableCell>Top Rules</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(data || []).map((row) => (
            <TableRow key={row.ip} hover>
              <TableCell sx={{ fontFamily: 'monospace', fontSize: 13 }}>{row.ip}</TableCell>
              <TableCell align="right">{row.count}</TableCell>
              <TableCell>{levelChip(row.maxLevel)}</TableCell>
              <TableCell sx={{ fontSize: 12 }}>
                {row.topRules?.map(r => sanitizeText(r)).join(', ') || '-'}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
