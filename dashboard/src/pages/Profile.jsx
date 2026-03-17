import { useState } from 'react';
import {
  Typography, Card, CardContent, TextField, Button, Alert, Box, Divider, Chip,
} from '@mui/material';
import { useAuth } from '../auth/AuthContext';

export default function Profile() {
  const { user, roles, getToken } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setMessage(null);

    if (newPassword !== confirmPassword) {
      setMessage({ type: 'error', text: 'Passwords do not match' });
      return;
    }
    if (newPassword.length < 8) {
      setMessage({ type: 'error', text: 'Password must be at least 8 characters' });
      return;
    }

    setLoading(true);
    try {
      const token = await getToken();
      const resp = await fetch('/auth/realms/night-watcher/account/credentials/password', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ currentPassword, newPassword, confirmation: newPassword }),
      });

      if (!resp.ok) {
        const err = await resp.json().catch(() => ({}));
        throw new Error(err.error_description || err.errorMessage || 'Failed to change password');
      }

      setMessage({ type: 'success', text: 'Password changed successfully' });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      setMessage({ type: 'error', text: err.message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Typography variant="h5" fontWeight={600} gutterBottom>Profile</Typography>

      <Card sx={{ maxWidth: 500, mb: 3 }}>
        <CardContent sx={{ px: 3, py: 2 }}>
          <Typography variant="subtitle2" color="text.secondary">Account Info</Typography>
          <Box sx={{ mt: 1 }}>
            <Typography variant="body2"><strong>Username:</strong> {user?.preferred_username || user?.sub || '—'}</Typography>
            <Typography variant="body2"><strong>Name:</strong> {user?.name || '—'}</Typography>
            <Typography variant="body2"><strong>Email:</strong> {user?.email || '—'}</Typography>
          </Box>

          <Box sx={{ mt: 1.5 }}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>Roles</Typography>
            <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
              {roles.filter(r => !r.startsWith('default-roles')).map(r => (
                <Chip key={r} label={r} size="small" variant="outlined" />
              ))}
            </Box>
          </Box>
        </CardContent>
      </Card>

      <Card sx={{ maxWidth: 500 }}>
        <CardContent sx={{ px: 3, py: 2 }}>
          <Typography variant="subtitle2" color="text.secondary">Change Password</Typography>
          <Divider sx={{ my: 1 }} />

          {message && <Alert severity={message.type} sx={{ mb: 1 }}>{message.text}</Alert>}

          <form onSubmit={handleChangePassword}>
            <TextField
              label="Current Password"
              type="password"
              fullWidth
              size="small"
              margin="dense"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
            <TextField
              label="New Password"
              type="password"
              fullWidth
              size="small"
              margin="dense"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
            <TextField
              label="Confirm New Password"
              type="password"
              fullWidth
              size="small"
              margin="dense"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
            <Button type="submit" variant="contained" size="small" disabled={loading} sx={{ mt: 1.5 }}>
              {loading ? 'Changing...' : 'Change Password'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </>
  );
}
