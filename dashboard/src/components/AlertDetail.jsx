import { Box, Typography, Chip } from '@mui/material';
import dayjs from 'dayjs';
import { sanitizeText } from '../utils/sanitize';

export default function AlertDetail({ event }) {
  if (!event) return null;

  return (
    <Box sx={{ p: 2, bgcolor: 'grey.50', fontSize: 13 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 1 }}>
        {event.rule?.groups?.map((g) => (
          <Chip key={g} label={sanitizeText(g)} size="small" variant="outlined" />
        ))}
      </Box>
      {event.rule?.mitre?.tactic && (
        <Typography variant="body2" gutterBottom>
          <strong>MITRE Tactic:</strong> {[].concat(event.rule.mitre.tactic).join(', ')}
        </Typography>
      )}
      {event.rule?.mitre?.technique && (
        <Typography variant="body2" gutterBottom>
          <strong>MITRE Technique:</strong> {[].concat(event.rule.mitre.technique).join(', ')}
        </Typography>
      )}
      {event.agent?.name && (
        <Typography variant="body2" gutterBottom>
          <strong>Agent:</strong> {event.agent.name}
        </Typography>
      )}
      {event.location && (
        <Typography variant="body2" gutterBottom>
          <strong>Location:</strong> {event.location}
        </Typography>
      )}
      <Typography variant="body2" gutterBottom>
        <strong>Timestamp:</strong> {dayjs(event.timestamp).format('YYYY-MM-DD HH:mm:ss')}
      </Typography>
      {event.full_log && (
        <Box
          sx={{
            mt: 1,
            p: 1,
            bgcolor: '#1e1e1e',
            color: '#d4d4d4',
            borderRadius: 1,
            fontFamily: 'monospace',
            fontSize: 12,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
            maxHeight: 300,
            overflow: 'auto',
          }}
        >
          {sanitizeText(event.full_log)}
        </Box>
      )}
    </Box>
  );
}
