import { useState } from 'react';
import {
  Typography, Card, CardContent, Box, Chip, Accordion, AccordionSummary, AccordionDetails,
  Table, TableBody, TableRow, TableCell, TableHead, TableContainer, Tabs, Tab,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ShieldIcon from '@mui/icons-material/Shield';
import { POLICY_TYPES, POLICIES, DATA_ACCESS_RULES } from '../api/gateway';

export default function GatewayPolicies() {
  const [tab, setTab] = useState(0);

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Gateway Policies</Typography>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Endpoint Policies" />
        <Tab label="Data Access Rules" />
      </Tabs>

      {tab === 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Each policy defines which endpoints a role can access. Requests are matched against the authenticated user's roles.
          </Typography>

          {POLICY_TYPES.map((p) => {
            const policy = POLICIES[p.type];
            if (!policy) return null;
            return (
              <Accordion key={p.type} defaultExpanded={p.type === 'CallingPortalUser'} sx={{ mb: 1 }}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, width: '100%' }}>
                    <ShieldIcon sx={{ color: p.color, fontSize: 20 }} />
                    <Typography fontWeight={600}>{p.label}</Typography>
                    <Chip label={`${policy.count} endpoints`} size="small" sx={{ fontSize: 11 }} />
                    {p.roles.map((r) => <Chip key={r} label={r} size="small" variant="outlined" sx={{ fontSize: 10, height: 20 }} />)}
                    <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto', mr: 2 }}>{p.description}</Typography>
                  </Box>
                </AccordionSummary>
                <AccordionDetails sx={{ pt: 0 }}>
                  {policy.categories ? (
                    policy.categories.map((cat) => (
                      <Box key={cat.name} sx={{ mb: 1.5 }}>
                        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>{cat.name}</Typography>
                        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                          {cat.endpoints.map((ep) => (
                            <Chip key={ep} label={ep} size="small" variant="outlined"
                              sx={{ fontSize: 11, height: 22, fontFamily: 'monospace' }} />
                          ))}
                        </Box>
                      </Box>
                    ))
                  ) : (
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {policy.endpoints?.map((ep) => (
                        <Chip key={ep} label={ep} size="small" variant="outlined"
                          sx={{ fontSize: 11, height: 22, fontFamily: 'monospace' }} />
                      ))}
                    </Box>
                  )}
                </AccordionDetails>
              </Accordion>
            );
          })}
        </>
      )}

      {tab === 1 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Data access rules enforce row-level security. The gateway extracts a field from the request payload (JSON Path)
            and compares it against the authenticated user's profile. Only matching requests are allowed.
          </Typography>

          <Card>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Endpoint Pattern</TableCell>
                    <TableCell>Payload Field (JSON Path)</TableCell>
                    <TableCell>Must Match</TableCell>
                    <TableCell>Match Type</TableCell>
                    <TableCell>Description</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {DATA_ACCESS_RULES.map((r) => (
                    <TableRow key={r.path + r.field} hover>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{r.path}</TableCell>
                      <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{r.field}</TableCell>
                      <TableCell>
                        <Chip label={`authUser.${r.authField}`} size="small" color="primary" variant="outlined" sx={{ fontSize: 11, fontFamily: 'monospace' }} />
                      </TableCell>
                      <TableCell><Chip label={r.match} size="small" sx={{ fontSize: 11 }} /></TableCell>
                      <TableCell><Typography variant="caption">{r.description}</Typography></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        </>
      )}
    </>
  );
}
