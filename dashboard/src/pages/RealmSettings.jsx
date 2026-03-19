import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, CardContent, Grid, TextField, Button, Alert, Box, Switch,
  FormControlLabel, Divider,
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import { useAuth } from '../auth/AuthContext';
import { getRealmSettings, updateRealmSettings } from '../auth/keycloak';

export default function RealmSettings() {
  const { getToken } = useAuth();
  const [settings, setSettings] = useState(null);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) return;
      const data = await getRealmSettings(token);
      setSettings(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => { load(); }, [load]);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const token = await getToken();
      await updateRealmSettings(token, {
        displayName: settings.displayName,
        accessTokenLifespan: settings.accessTokenLifespan,
        ssoSessionMaxLifespan: settings.ssoSessionMaxLifespan,
        ssoSessionIdleTimeout: settings.ssoSessionIdleTimeout,
        offlineSessionMaxLifespan: settings.offlineSessionMaxLifespan,
        accessTokenLifespanForImplicitFlow: settings.accessTokenLifespanForImplicitFlow,
        bruteForceProtected: settings.bruteForceProtected,
        maxFailureWaitSeconds: settings.maxFailureWaitSeconds,
        failureFactor: settings.failureFactor,
        waitIncrementSeconds: settings.waitIncrementSeconds,
        passwordPolicy: settings.passwordPolicy,
        registrationAllowed: settings.registrationAllowed,
        resetPasswordAllowed: settings.resetPasswordAllowed,
        rememberMe: settings.rememberMe,
        loginWithEmailAllowed: settings.loginWithEmailAllowed,
        duplicateEmailsAllowed: settings.duplicateEmailsAllowed,
        eventsEnabled: settings.eventsEnabled,
        adminEventsEnabled: settings.adminEventsEnabled,
      });
      setSuccess('Settings saved');
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const update = (field, value) => setSettings(prev => ({ ...prev, [field]: value }));

  if (loading || !settings) return <Typography>Loading realm settings...</Typography>;

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" fontWeight={600}>Realm Settings</Typography>
        <Button variant="contained" size="small" startIcon={<SaveIcon />} onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save Changes'}
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {success && <Alert severity="success" sx={{ mb: 2 }}>{success}</Alert>}

      <Grid container spacing={2}>
        {/* General */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ px: 3, py: 2 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>General</Typography>
              <Divider sx={{ mb: 1.5 }} />
              <TextField label="Display Name" fullWidth size="small" margin="dense"
                value={settings.displayName || ''} onChange={(e) => update('displayName', e.target.value)} />
              <TextField label="Realm ID" fullWidth size="small" margin="dense"
                value={settings.realm || ''} disabled />
              <FormControlLabel control={<Switch checked={settings.registrationAllowed || false}
                onChange={(e) => update('registrationAllowed', e.target.checked)} />} label="User Registration" />
              <FormControlLabel control={<Switch checked={settings.resetPasswordAllowed || false}
                onChange={(e) => update('resetPasswordAllowed', e.target.checked)} />} label="Forgot Password" />
              <FormControlLabel control={<Switch checked={settings.rememberMe || false}
                onChange={(e) => update('rememberMe', e.target.checked)} />} label="Remember Me" />
              <FormControlLabel control={<Switch checked={settings.loginWithEmailAllowed || false}
                onChange={(e) => update('loginWithEmailAllowed', e.target.checked)} />} label="Login with Email" />
            </CardContent>
          </Card>
        </Grid>

        {/* Tokens */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ px: 3, py: 2 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>Token Lifespans (seconds)</Typography>
              <Divider sx={{ mb: 1.5 }} />
              <TextField label="Access Token Lifespan" fullWidth size="small" margin="dense" type="number"
                value={settings.accessTokenLifespan || 300}
                onChange={(e) => update('accessTokenLifespan', parseInt(e.target.value))}
                helperText={`${Math.floor((settings.accessTokenLifespan || 300) / 60)} minutes`} />
              <TextField label="SSO Session Max Lifespan" fullWidth size="small" margin="dense" type="number"
                value={settings.ssoSessionMaxLifespan || 28800}
                onChange={(e) => update('ssoSessionMaxLifespan', parseInt(e.target.value))}
                helperText={`${Math.floor((settings.ssoSessionMaxLifespan || 28800) / 3600)} hours`} />
              <TextField label="SSO Session Idle Timeout" fullWidth size="small" margin="dense" type="number"
                value={settings.ssoSessionIdleTimeout || 1800}
                onChange={(e) => update('ssoSessionIdleTimeout', parseInt(e.target.value))}
                helperText={`${Math.floor((settings.ssoSessionIdleTimeout || 1800) / 60)} minutes`} />
              <TextField label="Offline Session Max Lifespan" fullWidth size="small" margin="dense" type="number"
                value={settings.offlineSessionMaxLifespan || 5184000}
                onChange={(e) => update('offlineSessionMaxLifespan', parseInt(e.target.value))}
                helperText={`${Math.floor((settings.offlineSessionMaxLifespan || 5184000) / 86400)} days`} />
            </CardContent>
          </Card>
        </Grid>

        {/* Brute Force */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ px: 3, py: 2 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>Brute Force Protection</Typography>
              <Divider sx={{ mb: 1.5 }} />
              <FormControlLabel control={<Switch checked={settings.bruteForceProtected || false}
                onChange={(e) => update('bruteForceProtected', e.target.checked)} />} label="Enabled" />
              <TextField label="Max Login Failures" fullWidth size="small" margin="dense" type="number"
                value={settings.failureFactor || 5}
                onChange={(e) => update('failureFactor', parseInt(e.target.value))} />
              <TextField label="Wait Increment (seconds)" fullWidth size="small" margin="dense" type="number"
                value={settings.waitIncrementSeconds || 60}
                onChange={(e) => update('waitIncrementSeconds', parseInt(e.target.value))} />
              <TextField label="Max Wait (seconds)" fullWidth size="small" margin="dense" type="number"
                value={settings.maxFailureWaitSeconds || 300}
                onChange={(e) => update('maxFailureWaitSeconds', parseInt(e.target.value))} />
            </CardContent>
          </Card>
        </Grid>

        {/* Security */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent sx={{ px: 3, py: 2 }}>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>Password Policy</Typography>
              <Divider sx={{ mb: 1.5 }} />
              <TextField label="Password Policy String" fullWidth size="small" margin="dense"
                value={settings.passwordPolicy || ''}
                onChange={(e) => update('passwordPolicy', e.target.value)}
                helperText="e.g. length(8) and upperCase(1) and digits(1) and specialChars(1)" />

              <Typography variant="subtitle2" fontWeight={600} sx={{ mt: 2 }} gutterBottom>Event Logging</Typography>
              <Divider sx={{ mb: 1.5 }} />
              <FormControlLabel control={<Switch checked={settings.eventsEnabled || false}
                onChange={(e) => update('eventsEnabled', e.target.checked)} />} label="Login Events" />
              <FormControlLabel control={<Switch checked={settings.adminEventsEnabled || false}
                onChange={(e) => update('adminEventsEnabled', e.target.checked)} />} label="Admin Events" />
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
