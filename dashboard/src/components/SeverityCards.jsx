import { Card, CardContent, Typography, Grid, Skeleton } from '@mui/material';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';

const SEVERITIES = [
  { key: 'critical', label: 'Critical', color: '#d32f2f', icon: <ErrorIcon /> },
  { key: 'high', label: 'High', color: '#f57c00', icon: <WarningIcon /> },
  { key: 'medium', label: 'Medium', color: '#fbc02d', icon: <InfoIcon /> },
  { key: 'low', label: 'Low', color: '#388e3c', icon: <CheckCircleIcon /> },
];

export default function SeverityCards({ counts, loading }) {
  return (
    <Grid container spacing={2}>
      {SEVERITIES.map((s) => (
        <Grid item xs={6} sm={3} key={s.key}>
          <Card sx={{ borderLeft: 4, borderColor: s.color }}>
            <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
              {loading ? (
                <Skeleton variant="text" width={60} height={40} />
              ) : (
                <Typography variant="h4" fontWeight={700} color={s.color}>
                  {counts?.[s.key] ?? 0}
                </Typography>
              )}
              <Typography variant="body2" color="text.secondary" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                {s.icon} {s.label}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}
