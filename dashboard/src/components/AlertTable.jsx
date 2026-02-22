import { useState } from 'react';
import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Chip, Collapse, IconButton, TablePagination, Typography, Box,
} from '@mui/material';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import dayjs from 'dayjs';
import AlertDetail from './AlertDetail';
import { sanitizeText } from '../utils/sanitize';

function levelChip(level) {
  if (level >= 12) return <Chip label={`${level} Crit`} size="small" color="error" />;
  if (level >= 8) return <Chip label={`${level} High`} size="small" sx={{ bgcolor: '#f57c00', color: '#fff' }} />;
  if (level >= 5) return <Chip label={`${level} Med`} size="small" sx={{ bgcolor: '#fbc02d', color: '#000' }} />;
  return <Chip label={`${level} Low`} size="small" color="success" />;
}

function AlertRow({ event }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <TableRow hover sx={{ cursor: 'pointer' }} onClick={() => setOpen(!open)}>
        <TableCell padding="checkbox">
          <IconButton size="small">
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell sx={{ fontSize: 12, whiteSpace: 'nowrap' }}>
          {dayjs(event.timestamp).format('MM-DD HH:mm:ss')}
        </TableCell>
        <TableCell>{levelChip(event.rule?.level)}</TableCell>
        <TableCell sx={{ fontSize: 13 }}>{sanitizeText(event.rule?.description)}</TableCell>
        <TableCell sx={{ fontSize: 12, fontFamily: 'monospace' }}>
          {event.data?.srcip || '-'}
        </TableCell>
        <TableCell sx={{ fontSize: 12, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {event.data?.url || '-'}
        </TableCell>
        <TableCell sx={{ fontSize: 12 }}>{event.location}</TableCell>
      </TableRow>
      <TableRow>
        <TableCell sx={{ py: 0 }} colSpan={7}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <AlertDetail event={event} />
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

export default function AlertTable({ events, total, page, rowsPerPage, onPageChange, onRowsPerPageChange, loading }) {
  if (!loading && (!events || events.length === 0)) {
    return (
      <Paper sx={{ p: 3, textAlign: 'center' }}>
        <Typography color="text.secondary">No events found for the selected filters</Typography>
      </Paper>
    );
  }

  return (
    <Paper>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox" />
              <TableCell>Time</TableCell>
              <TableCell>Level</TableCell>
              <TableCell>Description</TableCell>
              <TableCell>Source IP</TableCell>
              <TableCell>URL</TableCell>
              <TableCell>Log Source</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(events || []).map((e) => (
              <AlertRow key={e.id} event={e} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={total || 0}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={onPageChange}
        onRowsPerPageChange={onRowsPerPageChange}
        rowsPerPageOptions={[25, 50, 100]}
      />
    </Paper>
  );
}
