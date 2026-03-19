import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  Button, IconButton, TextField, Dialog, DialogTitle, DialogContent, DialogActions,
  Alert, Box, Chip, Tooltip, Switch, FormControlLabel,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { useAuth } from '../auth/AuthContext';
import { listClients, createClient, updateClient, deleteClient } from '../auth/keycloak';

const BUILTIN_CLIENTS = ['account', 'account-console', 'admin-cli', 'broker', 'realm-management', 'security-admin-console'];

export default function ClientManagement() {
  const { getToken } = useAuth();
  const [clients, setClients] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [editingClient, setEditingClient] = useState(null);
  const [form, setForm] = useState({
    clientId: '', name: '', publicClient: true, directAccessGrantsEnabled: true,
    standardFlowEnabled: true, redirectUris: '', webOrigins: '', enabled: true,
  });
  const [dialogError, setDialogError] = useState('');
  const [deleteTarget, setDeleteTarget] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) return;
      const data = await listClients(token);
      setClients(data.filter(c => !BUILTIN_CLIENTS.includes(c.clientId)));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { load(); }, [load]);

  const openCreate = () => {
    setDialogMode('create');
    setEditingClient(null);
    setForm({ clientId: '', name: '', publicClient: true, directAccessGrantsEnabled: true,
      standardFlowEnabled: true, redirectUris: '', webOrigins: '', enabled: true });
    setDialogError('');
    setDialogOpen(true);
  };

  const openEdit = (c) => {
    setDialogMode('edit');
    setEditingClient(c);
    setForm({
      clientId: c.clientId,
      name: c.name || '',
      publicClient: c.publicClient,
      directAccessGrantsEnabled: c.directAccessGrantsEnabled,
      standardFlowEnabled: c.standardFlowEnabled,
      redirectUris: (c.redirectUris || []).join('\n'),
      webOrigins: (c.webOrigins || []).join('\n'),
      enabled: c.enabled,
    });
    setDialogError('');
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setDialogError('');
    try {
      const token = await getToken();
      const data = {
        clientId: form.clientId,
        name: form.name,
        publicClient: form.publicClient,
        directAccessGrantsEnabled: form.directAccessGrantsEnabled,
        standardFlowEnabled: form.standardFlowEnabled,
        redirectUris: form.redirectUris.split('\n').map(s => s.trim()).filter(Boolean),
        webOrigins: form.webOrigins.split('\n').map(s => s.trim()).filter(Boolean),
        enabled: form.enabled,
      };
      if (dialogMode === 'create') {
        if (!form.clientId) throw new Error('Client ID is required');
        await createClient(token, data);
      } else {
        await updateClient(token, editingClient.id, { ...editingClient, ...data });
      }
      setDialogOpen(false);
      load();
    } catch (err) {
      setDialogError(err.message);
    }
  };

  const handleDelete = async () => {
    try {
      const token = await getToken();
      await deleteClient(token, deleteTarget.id);
      setDeleteTarget(null);
      load();
    } catch (err) {
      setError(err.message);
      setDeleteTarget(null);
    }
  };

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Client Management</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ mb: 2 }}>
        <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={openCreate}>Create Client</Button>
      </Box>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Client ID</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Flows</TableCell>
                <TableCell>Redirect URIs</TableCell>
                <TableCell>Enabled</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={7}>Loading...</TableCell></TableRow>
              ) : clients.length === 0 ? (
                <TableRow><TableCell colSpan={7}>No custom clients</TableCell></TableRow>
              ) : clients.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{c.clientId}</TableCell>
                  <TableCell>{c.name || '—'}</TableCell>
                  <TableCell>
                    <Chip label={c.publicClient ? 'Public' : 'Confidential'} size="small"
                      color={c.publicClient ? 'info' : 'warning'} variant="outlined" sx={{ fontSize: 11 }} />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                      {c.standardFlowEnabled && <Chip label="auth-code" size="small" sx={{ fontSize: 10, height: 18 }} />}
                      {c.directAccessGrantsEnabled && <Chip label="password" size="small" sx={{ fontSize: 10, height: 18 }} />}
                      {c.serviceAccountsEnabled && <Chip label="client-creds" size="small" sx={{ fontSize: 10, height: 18 }} />}
                      {c.implicitFlowEnabled && <Chip label="implicit" size="small" sx={{ fontSize: 10, height: 18 }} />}
                    </Box>
                  </TableCell>
                  <TableCell sx={{ fontSize: 11, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {(c.redirectUris || []).join(', ') || '—'}
                  </TableCell>
                  <TableCell>
                    <Chip label={c.enabled ? 'Yes' : 'No'} size="small" color={c.enabled ? 'success' : 'default'} sx={{ fontSize: 11 }} />
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Edit"><IconButton size="small" onClick={() => openEdit(c)}><EditIcon fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => setDeleteTarget(c)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{dialogMode === 'create' ? 'Create Client' : `Edit: ${editingClient?.clientId}`}</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <TextField label="Client ID" fullWidth size="small" margin="dense" value={form.clientId}
            onChange={(e) => setForm({ ...form, clientId: e.target.value })} disabled={dialogMode === 'edit'} required />
          <TextField label="Name" fullWidth size="small" margin="dense" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <Box sx={{ display: 'flex', gap: 2, mt: 1, flexWrap: 'wrap' }}>
            <FormControlLabel control={<Switch checked={form.publicClient} onChange={(e) => setForm({ ...form, publicClient: e.target.checked })} />}
              label="Public Client (SPA)" />
            <FormControlLabel control={<Switch checked={form.standardFlowEnabled} onChange={(e) => setForm({ ...form, standardFlowEnabled: e.target.checked })} />}
              label="Authorization Code" />
            <FormControlLabel control={<Switch checked={form.directAccessGrantsEnabled} onChange={(e) => setForm({ ...form, directAccessGrantsEnabled: e.target.checked })} />}
              label="Direct Access (Password)" />
            <FormControlLabel control={<Switch checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />}
              label="Enabled" />
          </Box>
          <TextField label="Redirect URIs (one per line)" fullWidth size="small" margin="dense"
            value={form.redirectUris} onChange={(e) => setForm({ ...form, redirectUris: e.target.value })}
            multiline rows={3} helperText="e.g. http://localhost:7100/* or https://app.example.com/*" />
          <TextField label="Web Origins (one per line)" fullWidth size="small" margin="dense"
            value={form.webOrigins} onChange={(e) => setForm({ ...form, webOrigins: e.target.value })}
            multiline rows={2} helperText="CORS origins. Use * to allow all." />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDialogOpen(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={handleSave}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete Client</DialogTitle>
        <DialogContent><Typography>Delete client <strong>{deleteTarget?.clientId}</strong>? Applications using this client will lose access.</Typography></DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
