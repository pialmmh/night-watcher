import { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Button, Table, TableHead, TableRow, TableCell, TableBody,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField, Stack, Alert,
  CircularProgress, Chip, Tooltip, IconButton, Divider,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import GroupIcon from '@mui/icons-material/Group';
import { listGroups, createGroup, createSubGroup, deleteGroup } from '../auth/keycloak';
import { useAuth } from '../auth/AuthContext';

// Party DB API (platform DB via :7105 — extend ha-cluster-api.py or a new party-api.py)
async function listTenants() {
  const resp = await fetch('/api/party/tenants');
  if (!resp.ok) return [];
  return resp.json();
}

async function createTenantParty(data) {
  const resp = await fetch('/api/party/tenants', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({}));
    throw new Error(err.error || 'Failed to create tenant in DB');
  }
  return resp.json();
}

async function deleteTenantParty(slug) {
  const resp = await fetch(`/api/party/tenants/${slug}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Failed to delete tenant from DB');
}

const EMPTY_FORM = {
  slug: '', displayName: '', shortName: '',
  email: '', phone: '',
  kbApiKey: '', odooPartnerId: '',
};

export default function TenantProvisioning() {
  const { accessToken: token } = useAuth();
  const [tenants, setTenants] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [dialog, setDialog] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [steps, setSteps] = useState([]); // progress steps during creation

  const [deleteDialog, setDeleteDialog] = useState({ open: false, tenant: null });

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [dbTenants, kcGroups] = await Promise.allSettled([listTenants(), listGroups(token)]);
      setTenants(dbTenants.status === 'fulfilled' ? dbTenants.value : []);
      setGroups(kcGroups.status === 'fulfilled' ? kcGroups.value : []);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const tenantsParentGroup = groups.find(g => g.name === 'tenants');

  const kcGroupExists = (slug) => {
    if (!tenantsParentGroup) return false;
    return tenantsParentGroup.subGroups?.some(sg => sg.name === slug);
  };

  const set = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }));

  const addStep = (label, status) =>
    setSteps(prev => [...prev, { label, status }]);

  const provision = async () => {
    const { slug, displayName } = form;
    if (!slug.trim()) { setFormError('Slug is required'); return; }
    if (!/^[a-z0-9-]+$/.test(slug)) { setFormError('Slug must be lowercase letters, digits, hyphens only'); return; }
    if (!displayName.trim()) { setFormError('Display name is required'); return; }

    setSaving(true);
    setFormError('');
    setSteps([]);

    try {
      // Step 1 — ensure /tenants parent group exists
      let tenantsGroup = tenantsParentGroup;
      if (!tenantsGroup) {
        addStep('Creating /tenants parent group in Keycloak…', 'loading');
        await createGroup(token, { name: 'tenants' });
        const refreshed = await listGroups(token);
        tenantsGroup = refreshed.find(g => g.name === 'tenants');
        setSteps(prev => prev.map((s, i) => i === prev.length - 1 ? { ...s, status: 'ok' } : s));
      }

      // Step 2 — create /tenants/{slug} sub-group
      addStep(`Creating Keycloak group /tenants/${slug}…`, 'loading');
      await createSubGroup(token, tenantsGroup.id, { name: slug });
      setSteps(prev => prev.map((s, i) => i === prev.length - 1 ? { ...s, status: 'ok' } : s));

      // Step 3 — insert party rows in platform DB
      addStep('Creating party + party_tenant rows in platform DB…', 'loading');
      try {
        await createTenantParty({
          slug: slug.trim(),
          display_name: displayName.trim(),
          short_name: form.shortName.trim() || slug.trim(),
          email: form.email.trim(),
          phone: form.phone.trim(),
          kb_api_key: form.kbApiKey.trim(),
          odoo_partner_id: form.odooPartnerId ? parseInt(form.odooPartnerId) : null,
          keycloak_group_path: `/tenants/${slug}`,
        });
        setSteps(prev => prev.map((s, i) => i === prev.length - 1 ? { ...s, status: 'ok' } : s));
      } catch (dbErr) {
        setSteps(prev => prev.map((s, i) => i === prev.length - 1 ? { ...s, status: 'warn', note: dbErr.message + ' (party DB not wired yet — Keycloak group created)' } : s));
      }

      await load();
      // keep dialog open to show steps; user closes manually
      setSaving(false);
    } catch (e) {
      setSteps(prev => [...prev.slice(0, -1), { ...prev[prev.length - 1], status: 'error', note: e.message }]);
      setFormError(e.message);
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    const { tenant } = deleteDialog;
    try {
      // Remove from Keycloak
      if (tenantsParentGroup) {
        const sg = tenantsParentGroup.subGroups?.find(g => g.name === tenant.slug);
        if (sg) await deleteGroup(token, sg.id);
      }
      // Remove from DB
      await deleteTenantParty(tenant.slug).catch(() => {});
      setDeleteDialog({ open: false, tenant: null });
      await load();
    } catch (e) {
      setError(e.message);
    }
  };

  const stepIcon = (status) => {
    if (status === 'loading') return <CircularProgress size={14} />;
    if (status === 'ok') return <CheckCircleIcon fontSize="small" color="success" />;
    if (status === 'warn') return <CheckCircleIcon fontSize="small" color="warning" />;
    return <ErrorIcon fontSize="small" color="error" />;
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h6" fontWeight={600}>Tenant Provisioning</Typography>
          <Typography variant="body2" color="text.secondary">
            One action creates the Keycloak group <code>/tenants/&#123;slug&#125;</code> and the platform DB party record.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} size="small" onClick={() => { setForm(EMPTY_FORM); setSteps([]); setFormError(''); setDialog(true); }}>
          Provision Tenant
        </Button>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell><strong>Slug</strong></TableCell>
              <TableCell><strong>Display Name</strong></TableCell>
              <TableCell><strong>Keycloak Group</strong></TableCell>
              <TableCell><strong>Odoo Partner</strong></TableCell>
              <TableCell align="right"><strong>Actions</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {tenants.length === 0 && groups.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>
                    No tenants yet. Click "Provision Tenant" to create the first one.
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              // Show from DB tenants; if DB not wired, fall back to KC groups under /tenants
              (tenants.length > 0 ? tenants : (tenantsParentGroup?.subGroups || []).map(sg => ({ slug: sg.name, display_name: sg.name, keycloak_group_path: sg.path }))).map(t => (
                <TableRow key={t.slug} hover>
                  <TableCell><code>{t.slug}</code></TableCell>
                  <TableCell>{t.display_name || t.slug}</TableCell>
                  <TableCell>
                    {kcGroupExists(t.slug)
                      ? <Chip icon={<GroupIcon />} label={`/tenants/${t.slug}`} size="small" color="success" variant="outlined" />
                      : <Chip label="No KC group" size="small" color="warning" variant="outlined" />}
                  </TableCell>
                  <TableCell>{t.odoo_partner_id || '—'}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Delete tenant">
                      <IconButton size="small" color="error" onClick={() => setDeleteDialog({ open: true, tenant: t })}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}

      {/* Provision Dialog */}
      <Dialog open={dialog} onClose={() => !saving && setDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Provision New Tenant</DialogTitle>
        <DialogContent sx={{ pt: 1.5 }}>
          {formError && <Alert severity="error" sx={{ mb: 1.5 }}>{formError}</Alert>}

          <Stack spacing={1.5} sx={{ px: 1 }}>
            <Stack direction="row" spacing={1.5}>
              <TextField label="Slug *" value={form.slug} onChange={set('slug')} size="small" fullWidth
                helperText="Lowercase, hyphens only. e.g. btcl, bdcom" />
              <TextField label="Display Name *" value={form.displayName} onChange={set('displayName')} size="small" fullWidth />
            </Stack>
            <Stack direction="row" spacing={1.5}>
              <TextField label="Short Name" value={form.shortName} onChange={set('shortName')} size="small" fullWidth
                helperText="Used in UI headers" />
              <TextField label="Email" value={form.email} onChange={set('email')} size="small" fullWidth type="email" />
            </Stack>
            <Stack direction="row" spacing={1.5}>
              <TextField label="Phone" value={form.phone} onChange={set('phone')} size="small" fullWidth />
              <TextField label="Odoo Partner ID" value={form.odooPartnerId} onChange={set('odooPartnerId')} size="small" fullWidth type="number" />
            </Stack>
            <TextField label="Kill Bill API Key" value={form.kbApiKey} onChange={set('kbApiKey')} size="small" fullWidth />
          </Stack>

          {steps.length > 0 && (
            <Box sx={{ mt: 2, px: 1 }}>
              <Divider sx={{ mb: 1.5 }} />
              <Typography variant="subtitle2" gutterBottom>Provisioning Steps</Typography>
              <Stack spacing={0.75}>
                {steps.map((s, i) => (
                  <Stack key={i} direction="row" alignItems="flex-start" spacing={1}>
                    {stepIcon(s.status)}
                    <Box>
                      <Typography variant="body2">{s.label}</Typography>
                      {s.note && <Typography variant="caption" color={s.status === 'warn' ? 'warning.main' : 'error'}>{s.note}</Typography>}
                    </Box>
                  </Stack>
                ))}
              </Stack>
            </Box>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDialog(false)} disabled={saving}>
            {steps.some(s => s.status === 'ok') ? 'Close' : 'Cancel'}
          </Button>
          {!steps.some(s => s.status === 'ok' && steps.filter(x => x.status !== 'warn').length >= 2) && (
            <Button variant="contained" onClick={provision} disabled={saving}>
              {saving ? <CircularProgress size={18} /> : 'Provision'}
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Delete Confirm */}
      <Dialog open={deleteDialog.open} onClose={() => setDeleteDialog({ open: false, tenant: null })} maxWidth="xs">
        <DialogTitle>Delete Tenant</DialogTitle>
        <DialogContent>
          <Typography>
            Delete tenant <strong>{deleteDialog.tenant?.slug}</strong>? This removes the Keycloak group and the party DB record. Users in the group will lose tenant access.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDeleteDialog({ open: false, tenant: null })}>Cancel</Button>
          <Button variant="contained" color="error" onClick={confirmDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
