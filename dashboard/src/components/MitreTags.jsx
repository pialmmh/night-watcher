import { Paper, Typography, Chip, Box, Skeleton } from '@mui/material';

export default function MitreTags({ data, loading }) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Skeleton variant="rectangular" height={80} />
      </Paper>
    );
  }

  const { tactics = [], techniques = [] } = data || {};

  if (!tactics.length && !techniques.length) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          MITRE ATT&CK
        </Typography>
        <Typography variant="body2" color="text.secondary">
          No MITRE data in current time range
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>
        MITRE ATT&CK
      </Typography>
      {tactics.length > 0 && (
        <Box sx={{ mb: 1 }}>
          <Typography variant="caption" color="text.secondary">Tactics</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
            {tactics.map((t) => (
              <Chip
                key={t.name}
                label={`${t.name} (${t.count})`}
                size="small"
                color="error"
                variant="outlined"
              />
            ))}
          </Box>
        </Box>
      )}
      {techniques.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.secondary">Techniques</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.5 }}>
            {techniques.map((t) => (
              <Chip
                key={t.name}
                label={`${t.name} (${t.count})`}
                size="small"
                color="warning"
                variant="outlined"
              />
            ))}
          </Box>
        </Box>
      )}
    </Paper>
  );
}
