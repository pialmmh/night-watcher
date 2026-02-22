import { ToggleButtonGroup, ToggleButton, Box } from '@mui/material';

const RANGES = [
  { label: '1h', value: 'now-1h' },
  { label: '6h', value: 'now-6h' },
  { label: '24h', value: 'now-24h' },
  { label: '7d', value: 'now-7d' },
  { label: '30d', value: 'now-30d' },
];

export default function DateRangePicker({ value, onChange }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
      <ToggleButtonGroup
        value={value}
        exclusive
        onChange={(_, v) => v && onChange(v)}
        size="small"
      >
        {RANGES.map((r) => (
          <ToggleButton key={r.value} value={r.value} sx={{ px: 1.5, py: 0.3, fontSize: 12 }}>
            {r.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
    </Box>
  );
}
