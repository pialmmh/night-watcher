import { Paper, Typography, Skeleton } from '@mui/material';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts';
import { sanitizeText } from '../utils/sanitize';

function levelColor(level) {
  if (level >= 12) return '#d32f2f';
  if (level >= 8) return '#f57c00';
  if (level >= 5) return '#fbc02d';
  return '#388e3c';
}

export default function TopRules({ data, loading }) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Skeleton variant="rectangular" height={280} />
      </Paper>
    );
  }

  const chartData = (data || []).map((d) => {
    const desc = sanitizeText(d.description);
    return {
      name: desc.length > 30 ? desc.slice(0, 28) + '...' : desc,
      fullName: desc,
      count: d.count,
      ruleId: d.ruleId,
      level: d.level,
      fill: levelColor(d.level),
    };
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>
        Top Fired Rules
      </Typography>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={chartData} layout="vertical" margin={{ left: 10 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis type="number" tick={{ fontSize: 11 }} />
          <YAxis
            type="category"
            dataKey="name"
            width={200}
            tick={{ fontSize: 10 }}
          />
          <Tooltip
            formatter={(value, name, props) => [value, `Rule ${props.payload.ruleId}`]}
            labelFormatter={(label, payload) => payload?.[0]?.payload?.fullName || label}
          />
          <Bar dataKey="count" name="Alerts" />
        </BarChart>
      </ResponsiveContainer>
    </Paper>
  );
}
