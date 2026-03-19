import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, CardContent, Table, TableHead, TableBody, TableRow, TableCell,
  TableContainer, Button, IconButton, TextField, Dialog, DialogTitle, DialogContent,
  DialogActions, Alert, Box, Chip, Switch, FormControlLabel, Tooltip,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import LockResetIcon from '@mui/icons-material/LockReset';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import BadgeIcon from '@mui/icons-material/Badge';
import LogoutIcon from '@mui/icons-material/Logout';
import { useAuth } from '../auth/AuthContext';
import {
  listUsers, createUser, updateUser, deleteUser, resetUserPassword,
  getUserRoles, getAvailableUserRoles, assignUserRoles, removeUserRoles, logoutUser,
} from '../auth/keycloak';

const SYSTEM_ROLES = ['default-roles-night-watcher', 'uma_authorization', 'offline_access'];

export default function UserManagement() {
  const { getToken } = useAuth();
  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  // Dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState('create'); // create | edit | password
  const [editUser, setEditUser] = useState(null);
  const [form, setForm] = useState({ username: '', email: '', firstName: '', lastName: '', enabled: true, password: '' });
  const [dialogError, setDialogError] = useState('');
  const [dialogLoading, setDialogLoading] = useState(false);

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState(null);

  // Role assignment
  const [roleDialogOpen, setRoleDialogOpen] = useState(false);
  const [roleUser, setRoleUser] = useState(null);
  const [userRolesAssigned, setUserRolesAssigned] = useState([]);
  const [userRolesAvailable, setUserRolesAvailable] = useState([]);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) return;
      const data = await listUsers(token, search, 0, 100);
      setUsers(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [getToken, search]);

  useEffect(() => { loadUsers(); }, [loadUsers]);

  const openCreate = () => {
    setDialogMode('create');
    setEditUser(null);
    setForm({ username: '', email: '', firstName: '', lastName: '', enabled: true, password: '' });
    setDialogError('');
    setDialogOpen(true);
  };

  const openEdit = (u) => {
    setDialogMode('edit');
    setEditUser(u);
    setForm({ username: u.username, email: u.email || '', firstName: u.firstName || '', lastName: u.lastName || '', enabled: u.enabled, password: '' });
    setDialogError('');
    setDialogOpen(true);
  };

  const openResetPassword = (u) => {
    setDialogMode('password');
    setEditUser(u);
    setForm({ ...form, password: '' });
    setDialogError('');
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setDialogError('');
    setDialogLoading(true);
    try {
      const token = await getToken();

      if (dialogMode === 'create') {
        if (!form.username) throw new Error('Username is required');
        if (!/^[a-zA-Z0-9._-]+$/.test(form.username)) throw new Error('Username must contain only letters, numbers, dots, hyphens, underscores');
        if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) throw new Error('Invalid email format');
        if (!form.password || form.password.length < 8) throw new Error('Password must be at least 8 characters');
        if (!/[A-Z]/.test(form.password)) throw new Error('Password must contain at least 1 uppercase letter');
        if (!/[0-9]/.test(form.password)) throw new Error('Password must contain at least 1 digit');
        await createUser(token, {
          username: form.username,
          email: form.email,
          firstName: form.firstName,
          lastName: form.lastName,
          enabled: form.enabled,
          credentials: [{ type: 'password', value: form.password, temporary: false }],
        });
      } else if (dialogMode === 'edit') {
        if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) throw new Error('Invalid email format');
        await updateUser(token, editUser.id, {
          email: form.email,
          firstName: form.firstName,
          lastName: form.lastName,
          enabled: form.enabled,
        });
      } else if (dialogMode === 'password') {
        if (!form.password || form.password.length < 8) throw new Error('Password must be at least 8 characters');
        if (!/[A-Z]/.test(form.password)) throw new Error('Password must contain at least 1 uppercase letter');
        if (!/[0-9]/.test(form.password)) throw new Error('Password must contain at least 1 digit');
        await resetUserPassword(token, editUser.id, form.password);
      }

      setDialogOpen(false);
      loadUsers();
    } catch (err) {
      setDialogError(err.message);
    } finally {
      setDialogLoading(false);
    }
  };

  const handleDelete = async () => {
    try {
      const token = await getToken();
      await deleteUser(token, deleteTarget.id);
      setDeleteTarget(null);
      loadUsers();
    } catch (err) {
      setError(err.message);
      setDeleteTarget(null);
    }
  };

  const openRoles = async (u) => {
    setRoleUser(u);
    setRoleDialogOpen(true);
    try {
      const token = await getToken();
      const [assigned, available] = await Promise.all([
        getUserRoles(token, u.id),
        getAvailableUserRoles(token, u.id),
      ]);
      setUserRolesAssigned(assigned.filter(r => !SYSTEM_ROLES.includes(r.name)));
      setUserRolesAvailable(available.filter(r => !SYSTEM_ROLES.includes(r.name)));
    } catch (err) { setError(err.message); }
  };

  const handleAssignRole = async (role) => {
    try {
      const token = await getToken();
      await assignUserRoles(token, roleUser.id, [role]);
      setUserRolesAssigned(prev => [...prev, role]);
      setUserRolesAvailable(prev => prev.filter(r => r.id !== role.id));
    } catch (err) { setError(err.message); }
  };

  const handleRemoveRole = async (role) => {
    try {
      const token = await getToken();
      await removeUserRoles(token, roleUser.id, [role]);
      setUserRolesAvailable(prev => [...prev, role]);
      setUserRolesAssigned(prev => prev.filter(r => r.id !== role.id));
    } catch (err) { setError(err.message); }
  };

  const handleLogoutUser = async (u) => {
    try {
      const token = await getToken();
      await logoutUser(token, u.id);
      setError(null);
    } catch (err) { setError(err.message); }
  };

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>User Management</Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ display: 'flex', gap: 1, mb: 2, alignItems: 'center' }}>
        <TextField
          size="small"
          placeholder="Search users..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ width: 260 }}
        />
        <Button variant="contained" size="small" startIcon={<PersonAddIcon />} onClick={openCreate}>
          Create User
        </Button>
      </Box>

      <Card>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Username</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Enabled</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5}>Loading...</TableCell></TableRow>
              ) : users.length === 0 ? (
                <TableRow><TableCell colSpan={5}>No users found</TableCell></TableRow>
              ) : users.map((u) => (
                <TableRow key={u.id} hover>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{[u.firstName, u.lastName].filter(Boolean).join(' ') || '—'}</TableCell>
                  <TableCell>{u.email || '—'}</TableCell>
                  <TableCell>
                    <Chip label={u.enabled ? 'Yes' : 'No'} size="small" color={u.enabled ? 'success' : 'default'} variant="outlined" />
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Roles"><IconButton size="small" color="primary" onClick={() => openRoles(u)}><BadgeIcon fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Edit"><IconButton size="small" onClick={() => openEdit(u)}><EditIcon fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Reset Password"><IconButton size="small" onClick={() => openResetPassword(u)}><LockResetIcon fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Logout"><IconButton size="small" onClick={() => handleLogoutUser(u)}><LogoutIcon fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => setDeleteTarget(u)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Create / Edit / Password Dialog */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>
          {dialogMode === 'create' ? 'Create User' : dialogMode === 'edit' ? `Edit: ${editUser?.username}` : `Reset Password: ${editUser?.username}`}
        </DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}

          {dialogMode !== 'password' && (
            <>
              <TextField
                label="Username"
                fullWidth
                size="small"
                margin="dense"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
                disabled={dialogMode === 'edit'}
                required
              />
              <TextField
                label="Email"
                fullWidth
                size="small"
                margin="dense"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
              <TextField
                label="First Name"
                fullWidth
                size="small"
                margin="dense"
                value={form.firstName}
                onChange={(e) => setForm({ ...form, firstName: e.target.value })}
              />
              <TextField
                label="Last Name"
                fullWidth
                size="small"
                margin="dense"
                value={form.lastName}
                onChange={(e) => setForm({ ...form, lastName: e.target.value })}
              />
              <FormControlLabel
                control={<Switch checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />}
                label="Enabled"
                sx={{ mt: 0.5 }}
              />
            </>
          )}

          {(dialogMode === 'create' || dialogMode === 'password') && (
            <TextField
              label={dialogMode === 'create' ? 'Initial Password' : 'New Password'}
              type="password"
              fullWidth
              size="small"
              margin="dense"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
              helperText="Minimum 8 characters"
            />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDialogOpen(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={handleSave} disabled={dialogLoading}>
            {dialogLoading ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete User</DialogTitle>
        <DialogContent>
          <Typography>Are you sure you want to delete <strong>{deleteTarget?.username}</strong>?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button variant="contained" color="error" onClick={handleDelete}>Delete</Button>
        </DialogActions>
      </Dialog>

      {/* Role Assignment Dialog */}
      <Dialog open={roleDialogOpen} onClose={() => setRoleDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Roles: {roleUser?.username}</DialogTitle>
        <DialogContent>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>Assigned Roles</Typography>
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 2, minHeight: 28 }}>
            {userRolesAssigned.length === 0 && <Typography variant="caption">No roles assigned</Typography>}
            {userRolesAssigned.map(r => (
              <Chip key={r.id} label={r.name} size="small" color="primary" onDelete={() => handleRemoveRole(r)} />
            ))}
          </Box>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>Available Roles</Typography>
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', minHeight: 28 }}>
            {userRolesAvailable.length === 0 && <Typography variant="caption">No more roles available</Typography>}
            {userRolesAvailable.map(r => (
              <Chip key={r.id} label={r.name} size="small" variant="outlined" onClick={() => handleAssignRole(r)}
                sx={{ cursor: 'pointer', '&:hover': { bgcolor: 'primary.50' } }} />
            ))}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setRoleDialogOpen(false)} size="small">Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
