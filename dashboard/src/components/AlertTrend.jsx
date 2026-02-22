import { Paper, Typography, Skeleton } from '@mui/material';
import {
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, Legend, CartesianGrid,
} from 'recharts';
import dayjs from 'dayjs';

export default function AlertTrend({ data, loading }) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Skeleton variant="rectangular" height={280} />
      </Paper>
    );
  }

  const formatted = (data || []).map((d) => ({
    ...d,
    label: dayjs(d.ts).format('HH:mm'),
  }));

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>
        Alert Trend (last 24h)
      </Typography>
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={formatted}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="label" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
          <YAxis tick={{ fontSize: 11 }} />
          <Tooltip
            labelFormatter={(_, payload) => {
              if (payload?.[0]) return dayjs(payload[0].payload.ts).format('MMM D, HH:mm');
              return '';
            }}
          />
          <Legend />
          <Line
            type="monotone"
            dataKey="security"
            stroke="#d32f2f"
            name="Security"
            strokeWidth={2}
            dot={false}
          />
          <Line
            type="monotone"
            dataKey="watchdog"
            stroke="#1976d2"
            name="Watchdog"
            strokeWidth={2}
            dot={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </Paper>
  );
}
