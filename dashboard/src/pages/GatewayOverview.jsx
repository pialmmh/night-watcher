import { Typography, Grid, Card, CardContent, Box, Chip, LinearProgress } from '@mui/material';
import ShieldIcon from '@mui/icons-material/Shield';
import PolicyIcon from '@mui/icons-material/Policy';
import PublicIcon from '@mui/icons-material/Public';
import RuleIcon from '@mui/icons-material/Rule';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import BlockIcon from '@mui/icons-material/Block';
import { POLICY_TYPES, DATA_ACCESS_RULES, PUBLIC_ENDPOINT_CATEGORIES } from '../api/gateway';

const totalPublic = PUBLIC_ENDPOINT_CATEGORIES.reduce((s, c) => s + c.count, 0);

const stats = [
  { label: 'Auth Policies', value: POLICY_TYPES.length, icon: <PolicyIcon />, color: '#1565c0' },
  { label: 'Public Endpoints', value: totalPublic, icon: <LockOpenIcon />, color: '#2e7d32' },
  { label: 'Data Access Rules', value: DATA_ACCESS_RULES.length, icon: <RuleIcon />, color: '#e65100' },
  { label: 'Gateway Port', value: '8001', icon: <PublicIcon />, color: '#616161' },
];

export default function GatewayOverview() {
  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>API Gateway</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Spring Cloud Gateway — policy-based RBAC, data-level authorization, audit logging
      </Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {stats.map((s) => (
          <Grid item xs={6} md={3} key={s.label}>
            <Card>
              <CardContent sx={{ py: 1.5, px: 2, '&:last-child': { pb: 1.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  <Box sx={{ color: s.color }}>{s.icon}</Box>
                  <Typography variant="body2" color="text.secondary">{s.label}</Typography>
                </Box>
                <Typography variant="h5" fontWeight={700}>{s.value}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Typography variant="h6" fontWeight={600} gutterBottom>Authorization Policies</Typography>
      <Grid container spacing={1.5} sx={{ mb: 3 }}>
        {POLICY_TYPES.map((p) => (
          <Grid item xs={12} sm={6} md={3} key={p.type}>
            <Card sx={{ borderLeft: `4px solid ${p.color}` }}>
              <CardContent sx={{ py: 1.5, px: 2, '&:last-child': { pb: 1.5 } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  {p.type === 'Deny' ? <BlockIcon sx={{ color: p.color, fontSize: 20 }} /> : <ShieldIcon sx={{ color: p.color, fontSize: 20 }} />}
                  <Typography variant="subtitle2" fontWeight={600}>{p.label}</Typography>
                </Box>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>{p.description}</Typography>
                <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                  {p.roles.map((r) => <Chip key={r} label={r} size="small" sx={{ fontSize: 10, height: 20 }} />)}
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Typography variant="h6" fontWeight={600} gutterBottom>Request Flow</Typography>
      <Card sx={{ mb: 3 }}>
        <CardContent sx={{ py: 2, px: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            {['Client Request', 'Extract Token', 'Validate with Keycloak', 'Match Policy', 'Check Endpoint Access', 'Data Access Rules', 'Audit Log', 'Forward to Service'].map((step, i) => (
              <Box key={step} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Chip label={`${i + 1}. ${step}`} size="small" variant={i === 2 ? 'filled' : 'outlined'} color={i === 2 ? 'primary' : 'default'} />
                {i < 7 && <Typography color="text.disabled">→</Typography>}
              </Box>
            ))}
          </Box>
        </CardContent>
      </Card>

      <Typography variant="h6" fontWeight={600} gutterBottom>Backend Services</Typography>
      <Grid container spacing={1.5}>
        {[
          { name: 'AUTHENTICATION', desc: 'User auth, API keys, permissions', port: '8080' },
          { name: 'FREESWITCHREST', desc: 'PBX, call center, CDR, recordings', port: '8080' },
          { name: 'SMSREST', desc: 'SMS campaigns, templates, contacts', port: '8082' },
        ].map((svc) => (
          <Grid item xs={12} sm={4} key={svc.name}>
            <Card variant="outlined">
              <CardContent sx={{ py: 1.5, px: 2, '&:last-child': { pb: 1.5 } }}>
                <Typography variant="subtitle2" fontWeight={600}>{svc.name}</Typography>
                <Typography variant="caption" color="text.secondary">{svc.desc}</Typography>
                <Typography variant="caption" display="block" color="text.secondary">Eureka → :{svc.port}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </>
  );
}
