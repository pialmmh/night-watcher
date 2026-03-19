import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  Button, IconButton, TextField, Dialog, DialogTitle, DialogContent, DialogActions,
  Alert, Box, Chip, Tooltip, Collapse, List, ListItem, ListItemText,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { useAuth } from '../auth/AuthContext';
import { listRoles, createRole, updateRole, deleteRole, getRoleUsers } from '../auth/keycloak';

const SYSTEM_ROLES = ['default-roles-night-watcher', 'uma_authorization', 'offline_access'];

export default function RoleManagement() {
  const { getToken } = useAuth();
  const [roles, setRoles] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [expandedRole, setExpandedRole] = useState(null);
  const [roleUsers, setRoleUsers] = useState({});

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create');
  const [editingRole, setEditingRole] = useState(null);
  const [form, setForm] = useState({ name: '', description: '' });
  const [dialogError, setDialogError] = useState('');

  const [deleteTarget, setDeleteTarget] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) return;
      const data = await listRoles(token);
      setRoles(data.filter(r => !SYSTEM_ROLES.includes(r.name)));
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { load(); }, [load]);

  const toggleExpand = async (roleName) => {
    if (expandedRole === roleName) {
      setExpandedRole(null);
      return;
    }
    setExpandedRole(roleName);
    if (!roleUsers[roleName]) {
      try {
        const token = await getToken();
        const users = await getRoleUsers(token, roleName);
        setRoleUsers(prev => ({ ...prev, [roleName]: users }));
      } catch { /* ignore */ }
    }
  };

  const openCreate = () => {
    setDialogMode('create');
    setEditingRole(null);
    setForm({ name: '', description: '' });
    setDialogError('');
    setDialogOpen(true);
  };

  const openEdit = (role) => {
    setDialogMode('edit');
    setEditingRole(role);
    setForm({ name: role.name, description: role.description || '' });
    setDialogError('');
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setDialogError('');
    try {
      const token = await getToken();
      if (dialogMode === 'create') {
        if (!form.name) throw new Error('Role name is required');
        if (!/^[a-zA-Z0-9_-]+$/.test(form.name)) throw new Error('Role name must contain only letters, numbers, hyphens, underscores');
        if (form.name.length > 255) throw new Error('Role name must be 255 characters or less');
        await createRole(token, { name: form.name, description: form.description });
      } else {
        await updateRole(token, editingRole.name, { ...editingRole, description: form.description });
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
      await deleteRole(token, deleteTarget.name);
      setDeleteTarget(null);
      load();
    } catch (err) {
      setError(err.message);
      setDeleteTarget(null);
    }
  };

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Role Management</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ mb: 2 }}>
        <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={openCreate}>Create Role</Button>
      </Box>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell width={40}></TableCell>
                <TableCell>Role Name</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Composite</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5}>Loading...</TableCell></TableRow>
              ) : roles.map((r) => (
                <>
                  <TableRow key={r.id} hover>
                    <TableCell>
                      <IconButton size="small" onClick={() => toggleExpand(r.name)}>
                        {expandedRole === r.name ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                      </IconButton>
                    </TableCell>
                    <TableCell><Chip label={r.name} size="small" variant="outlined" /></TableCell>
                    <TableCell><Typography variant="caption">{r.description || '—'}</Typography></TableCell>
                    <TableCell><Chip label={r.composite ? 'Yes' : 'No'} size="small" color={r.composite ? 'primary' : 'default'} sx={{ fontSize: 11 }} /></TableCell>
                    <TableCell align="right">
                      <Tooltip title="Edit"><IconButton size="small" onClick={() => openEdit(r)}><EditIcon fontSize="small" /></IconButton></Tooltip>
                      <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => setDeleteTarget(r)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                    </TableCell>
                  </TableRow>
                  {expandedRole === r.name && (
                    <TableRow>
                      <TableCell colSpan={5} sx={{ py: 0, bgcolor: 'grey.50' }}>
                        <Collapse in>
                          <Box sx={{ px: 2, py: 1 }}>
                            <Typography variant="subtitle2" color="text.secondary">Users with this role:</Typography>
                            {roleUsers[r.name]?.length === 0 && <Typography variant="caption">No users</Typography>}
                            <List dense disablePadding>
                              {(roleUsers[r.name] || []).map(u => (
                                <ListItem key={u.id} disablePadding>
                                  <ListItemText primary={u.username} secondary={u.email} primaryTypographyProps={{ fontSize: 13 }} secondaryTypographyProps={{ fontSize: 11 }} />
                                </ListItem>
                              ))}
                            </List>
                          </Box>
                        </Collapse>
                      </TableCell>
                    </TableRow>
                  )}
                </>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{dialogMode === 'create' ? 'Create Role' : `Edit: ${editingRole?.name}`}</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <TextField label="Role Name" fullWidth size="small" margin="dense" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} disabled={dialogMode === 'edit'} required />
          <TextField label="Description" fullWidth size="small" margin="dense" value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })} multiline rows={2} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDialogOpen(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={handleSave}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete Role</DialogTitle>
        <DialogContent><Typography>Delete role <strong>{deleteTarget?.name}</strong>? Users with this role will lose it.</Typography></DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
