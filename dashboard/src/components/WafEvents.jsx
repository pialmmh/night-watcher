import { Paper, Typography, Skeleton } from '@mui/material';
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip, Legend } from 'recharts';
import { sanitizeText } from '../utils/sanitize';

const COLORS = ['#d32f2f', '#f57c00', '#fbc02d', '#1976d2', '#388e3c', '#7b1fa2', '#0097a7', '#455a64'];

export default function WafAttackTypes({ data, loading }) {
  if (loading) {
    return (
      <Paper sx={{ p: 2 }}>
        <Skeleton variant="rectangular" height={280} />
      </Paper>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          Attack Type Breakdown
        </Typography>
        <Typography variant="body2" color="text.secondary">
          No WAF events in current time range
        </Typography>
      </Paper>
    );
  }

  const chartData = data.map((d) => {
    const desc = sanitizeText(d.description);
    return {
      name: desc.length > 35 ? desc.slice(0, 33) + '...' : desc,
      value: d.count,
      ruleId: d.ruleId,
    };
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>
        Attack Type Breakdown
      </Typography>
      <ResponsiveContainer width="100%" height={280}>
        <PieChart>
          <Pie
            data={chartData}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="50%"
            outerRadius={100}
            label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
            labelLine
          >
            {chartData.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip />
        </PieChart>
      </ResponsiveContainer>
    </Paper>
  );
}
