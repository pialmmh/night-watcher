import { Card, CardContent, Typography, Grid, Chip, Box, Skeleton } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import dayjs from 'dayjs';
import { sanitizeText } from '../utils/sanitize';

export default function WatchdogStatus({ data, loading }) {
  if (loading) {
    return (
      <Grid container spacing={2}>
        {[0, 1, 2].map((i) => (
          <Grid item xs={12} sm={6} md={4} key={i}>
            <Skeleton variant="rectangular" height={100} />
          </Grid>
        ))}
      </Grid>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No watchdog events in current time range
      </Typography>
    );
  }

  return (
    <Grid container spacing={2}>
      {data.map((item) => {
        const isHealthCheck = String(item.ruleId) === '100700';
        const isFailover = String(item.ruleId) === '100701';
        return (
          <Grid item xs={12} sm={6} md={4} key={item.ruleId}>
            <Card sx={{ borderLeft: 4, borderColor: isFailover ? 'error.main' : 'success.main' }}>
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  {isFailover ? (
                    <ErrorIcon color="error" fontSize="small" />
                  ) : (
                    <CheckCircleIcon color="success" fontSize="small" />
                  )}
                  <Typography variant="subtitle2" fontWeight={600}>
                    Rule {item.ruleId}
                  </Typography>
                  <Chip
                    label={`${item.count} events`}
                    size="small"
                    color={isFailover ? 'error' : 'default'}
                    variant="outlined"
                  />
                </Box>
                <Typography variant="body2">{sanitizeText(item.description)}</Typography>
                {item.latest && (
                  <Typography variant="caption" color="text.secondary">
                    Last: {dayjs(item.latest.timestamp).format('HH:mm:ss')}
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        );
      })}
    </Grid>
  );
}
