import { useState, useEffect, useCallback } from 'react';
import {
  Typography, Card, CardContent, Table, TableHead, TableBody, TableRow, TableCell,
  TableContainer, Button, IconButton, TextField, Dialog, DialogTitle, DialogContent,
  DialogActions, Alert, Box, Chip, Tooltip, Grid, Switch, FormControlLabel,
  Tabs, Tab, Select, MenuItem, FormControl, InputLabel, Accordion, AccordionSummary,
  AccordionDetails, Divider,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';
import DnsIcon from '@mui/icons-material/Dns';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import HealthAndSafetyIcon from '@mui/icons-material/HealthAndSafety';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  listClusters, getClusterFull, createCluster, updateCluster, deleteCluster,
  createNode, updateNode, deleteNode,
  createGroup, deleteGroup,
  createResource, updateResource, deleteResource,
  createCheck, updateCheck, deleteCheck,
} from '../api/ha-cluster';

export default function HaClusterManager() {
  const [clusters, setClusters] = useState([]);
  const [selectedCluster, setSelectedCluster] = useState(null);
  const [clusterFull, setClusterFull] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState(0);

  // Dialogs
  const [clusterDialog, setClusterDialog] = useState(false);
  const [clusterForm, setClusterForm] = useState({});
  const [clusterMode, setClusterMode] = useState('create');

  const [nodeDialog, setNodeDialog] = useState(false);
  const [nodeForm, setNodeForm] = useState({});
  const [nodeMode, setNodeMode] = useState('create');

  const [resourceDialog, setResourceDialog] = useState(false);
  const [resourceForm, setResourceForm] = useState({});
  const [resourceGroupId, setResourceGroupId] = useState(null);

  const [checkDialog, setCheckDialog] = useState(false);
  const [checkForm, setCheckForm] = useState({});
  const [checkGroupId, setCheckGroupId] = useState(null);

  const [groupDialog, setGroupDialog] = useState(false);
  const [groupForm, setGroupForm] = useState({});

  const [deleteDialog, setDeleteDialog] = useState({ open: false, type: '', id: null, name: '' });

  const [dialogError, setDialogError] = useState('');

  const loadClusters = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listClusters();
      setClusters(data);
      if (data.length > 0 && !selectedCluster) setSelectedCluster(data[0].id);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, []);

  const loadClusterFull = useCallback(async () => {
    if (!selectedCluster) return;
    try {
      const data = await getClusterFull(selectedCluster);
      setClusterFull(data);
    } catch (e) { setError(e.message); }
  }, [selectedCluster]);

  useEffect(() => { loadClusters(); }, [loadClusters]);
  useEffect(() => { loadClusterFull(); }, [loadClusterFull]);

  const reload = () => { loadClusters(); loadClusterFull(); };

  // ── Cluster CRUD ──
  const openCreateCluster = () => {
    setClusterMode('create');
    setClusterForm({ name: '', tenant: '', quorum: 2, fail_threshold: 3, check_interval_sec: 5,
      auto_failback: false, max_failovers: 3, failover_window_sec: 3600, observation_stale_sec: 30,
      consul_address: '127.0.0.1:8500', enabled: true });
    setDialogError('');
    setClusterDialog(true);
  };
  const openEditCluster = () => {
    if (!clusterFull) return;
    setClusterMode('edit');
    setClusterForm({ ...clusterFull });
    setDialogError('');
    setClusterDialog(true);
  };
  const saveCluster = async () => {
    setDialogError('');
    try {
      if (!clusterForm.name) throw new Error('Cluster name is required');
      if (!clusterForm.tenant) throw new Error('Tenant is required');
      if (clusterMode === 'create') {
        const r = await createCluster(clusterForm);
        setSelectedCluster(r.id);
      } else {
        await updateCluster(clusterFull.id, clusterForm);
      }
      setClusterDialog(false);
      reload();
    } catch (e) { setDialogError(e.message); }
  };

  // ── Node CRUD ──
  const openCreateNode = () => {
    setNodeMode('create');
    setNodeForm({ node_id: '', address: '', priority: (clusterFull?.nodes?.length || 0) + 1,
      ssh_user: 'root', ssh_key_path: '/root/.ssh/id_rsa', ssh_port: 22, fence_cmd: '' });
    setDialogError('');
    setNodeDialog(true);
  };
  const openEditNode = (n) => {
    setNodeMode('edit');
    setNodeForm({ ...n });
    setDialogError('');
    setNodeDialog(true);
  };
  const saveNode = async () => {
    setDialogError('');
    try {
      if (!nodeForm.node_id) throw new Error('Node ID is required');
      if (!nodeForm.address) throw new Error('Address is required');
      if (nodeMode === 'create') {
        await createNode(selectedCluster, nodeForm);
      } else {
        await updateNode(nodeForm.id, nodeForm);
      }
      setNodeDialog(false);
      loadClusterFull();
    } catch (e) { setDialogError(e.message); }
  };

  // ── Group CRUD ──
  const openCreateGroup = () => {
    setGroupForm({ group_id: '', sort_order: (clusterFull?.groups?.length || 0) + 1 });
    setDialogError('');
    setGroupDialog(true);
  };
  const saveGroup = async () => {
    setDialogError('');
    try {
      if (!groupForm.group_id) throw new Error('Group ID is required');
      await createGroup(selectedCluster, groupForm);
      setGroupDialog(false);
      loadClusterFull();
    } catch (e) { setDialogError(e.message); }
  };

  // ── Resource CRUD ──
  const openCreateResource = (gid) => {
    setResourceGroupId(gid);
    setResourceForm({ resource_id: '', type: 'action', sort_order: 0,
      vip_ip: '', vip_cidr: 24, vip_interface: 'eth0',
      activate_cmd: '', deactivate_cmd: '', check_cmd: '', cmd_timeout_sec: 30 });
    setDialogError('');
    setResourceDialog(true);
  };
  const saveResource = async () => {
    setDialogError('');
    try {
      if (!resourceForm.resource_id) throw new Error('Resource ID is required');
      if (resourceForm.type === 'vip' && !resourceForm.vip_ip) throw new Error('VIP IP is required');
      if (resourceForm.type === 'action' && !resourceForm.activate_cmd) throw new Error('Activate command is required');
      await createResource(resourceGroupId, resourceForm);
      setResourceDialog(false);
      loadClusterFull();
    } catch (e) { setDialogError(e.message); }
  };

  // ── Check CRUD ──
  const openCreateCheck = (gid) => {
    setCheckGroupId(gid);
    setCheckForm({ name: '', type: 'http', target: '', expect_str: '', interval_sec: 5, timeout_sec: 3, scope: 'cluster' });
    setDialogError('');
    setCheckDialog(true);
  };
  const saveCheck = async () => {
    setDialogError('');
    try {
      if (!checkForm.name) throw new Error('Check name is required');
      if (!checkForm.target) throw new Error('Target is required');
      await createCheck(checkGroupId, checkForm);
      setCheckDialog(false);
      loadClusterFull();
    } catch (e) { setDialogError(e.message); }
  };

  // ── Delete ──
  const confirmDelete = (type, id, name) => setDeleteDialog({ open: true, type, id, name });
  const executeDelete = async () => {
    try {
      const fn = { cluster: deleteCluster, node: deleteNode, group: deleteGroup, resource: deleteResource, check: deleteCheck }[deleteDialog.type];
      await fn(deleteDialog.id);
      setDeleteDialog({ open: false, type: '', id: null, name: '' });
      if (deleteDialog.type === 'cluster') { setSelectedCluster(null); setClusterFull(null); loadClusters(); }
      else { loadClusterFull(); }
    } catch (e) { setError(e.message); }
  };

  const cf = (field, val) => setClusterForm(p => ({ ...p, [field]: val }));
  const nf = (field, val) => setNodeForm(p => ({ ...p, [field]: val }));
  const rf = (field, val) => setResourceForm(p => ({ ...p, [field]: val }));
  const kf = (field, val) => setCheckForm(p => ({ ...p, [field]: val }));

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5" fontWeight={600}>HA Cluster Manager</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button size="small" startIcon={<RefreshIcon />} onClick={reload}>Refresh</Button>
          <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={openCreateCluster}>New Cluster</Button>
        </Box>
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}

      {/* Cluster selector */}
      <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
        {clusters.map(c => (
          <Chip key={c.id} label={`${c.name} (${c.node_count} nodes)`}
            color={selectedCluster === c.id ? 'primary' : 'default'}
            variant={selectedCluster === c.id ? 'filled' : 'outlined'}
            onClick={() => setSelectedCluster(c.id)}
            icon={<DeviceHubIcon />} />
        ))}
      </Box>

      {clusterFull && (
        <>
          {/* Cluster info bar */}
          <Card sx={{ mb: 2 }}>
            <CardContent sx={{ py: 1.5, px: 3, '&:last-child': { pb: 1.5 } }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                <Typography fontWeight={600}>{clusterFull.name}</Typography>
                <Chip label={clusterFull.tenant} size="small" variant="outlined" />
                <Chip label={clusterFull.enabled ? 'Enabled' : 'Disabled'} size="small" color={clusterFull.enabled ? 'success' : 'default'} />
                <Chip label={`Quorum: ${clusterFull.quorum}`} size="small" />
                <Chip label={`Fail: ${clusterFull.fail_threshold}x`} size="small" />
                <Chip label={`Interval: ${clusterFull.check_interval_sec}s`} size="small" />
                <Chip label={clusterFull.auto_failback ? 'Auto-failback' : 'No failback'} size="small" color={clusterFull.auto_failback ? 'warning' : 'default'} />
                <Box sx={{ ml: 'auto' }}>
                  <Tooltip title="Edit Cluster"><IconButton size="small" onClick={openEditCluster}><EditIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Delete Cluster"><IconButton size="small" color="error" onClick={() => confirmDelete('cluster', clusterFull.id, clusterFull.name)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                </Box>
              </Box>
            </CardContent>
          </Card>

          <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
            <Tab label={`Nodes (${clusterFull.nodes?.length || 0})`} />
            <Tab label={`Resource Groups (${clusterFull.groups?.length || 0})`} />
            <Tab label="Status" />
          </Tabs>

          {/* ── Nodes Tab ── */}
          {tab === 0 && (
            <>
              <Box sx={{ mb: 1 }}>
                <Button size="small" startIcon={<AddIcon />} onClick={openCreateNode}>Add Node</Button>
              </Box>
              <Card>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Node ID</TableCell>
                        <TableCell>Address</TableCell>
                        <TableCell>Priority</TableCell>
                        <TableCell>SSH</TableCell>
                        <TableCell>Fence</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(clusterFull.nodes || []).map(n => (
                        <TableRow key={n.id} hover>
                          <TableCell><Chip label={n.node_id} size="small" icon={<DnsIcon />} /></TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{n.address}</TableCell>
                          <TableCell><Chip label={n.priority} size="small" /></TableCell>
                          <TableCell sx={{ fontSize: 12 }}>{n.ssh_user}@:{n.ssh_port}</TableCell>
                          <TableCell sx={{ fontSize: 11, fontFamily: 'monospace', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>{n.fence_cmd || '—'}</TableCell>
                          <TableCell align="right">
                            <IconButton size="small" onClick={() => openEditNode(n)}><EditIcon fontSize="small" /></IconButton>
                            <IconButton size="small" color="error" onClick={() => confirmDelete('node', n.id, n.node_id)}><DeleteIcon fontSize="small" /></IconButton>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Card>
            </>
          )}

          {/* ── Resource Groups Tab ── */}
          {tab === 1 && (
            <>
              <Box sx={{ mb: 1 }}>
                <Button size="small" startIcon={<AddIcon />} onClick={openCreateGroup}>Add Group</Button>
              </Box>
              {(clusterFull.groups || []).map(g => (
                <Accordion key={g.id} defaultExpanded>
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, width: '100%' }}>
                      <Typography fontWeight={600}>{g.group_id}</Typography>
                      <Chip label={`${(g.resources || []).length} resources`} size="small" />
                      <Chip label={`${(g.checks || []).length} checks`} size="small" />
                      <Box sx={{ ml: 'auto', mr: 2 }}>
                        <IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); confirmDelete('group', g.id, g.group_id); }}><DeleteIcon fontSize="small" /></IconButton>
                      </Box>
                    </Box>
                  </AccordionSummary>
                  <AccordionDetails>
                    {/* Resources */}
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                      <Typography variant="subtitle2" color="text.secondary">Resources (activation order)</Typography>
                      <Button size="small" startIcon={<AddIcon />} sx={{ ml: 'auto' }} onClick={() => openCreateResource(g.id)}>Add</Button>
                    </Box>
                    <TableContainer sx={{ mb: 2 }}>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>#</TableCell>
                            <TableCell>ID</TableCell>
                            <TableCell>Type</TableCell>
                            <TableCell>Config</TableCell>
                            <TableCell align="right"></TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {(g.resources || []).map((r, i) => (
                            <TableRow key={r.id} hover>
                              <TableCell>{i + 1}</TableCell>
                              <TableCell><Chip label={r.resource_id} size="small" /></TableCell>
                              <TableCell>
                                <Chip label={r.type} size="small" color={r.type === 'vip' ? 'primary' : r.type === 'action' ? 'warning' : 'default'} />
                              </TableCell>
                              <TableCell sx={{ fontSize: 11, fontFamily: 'monospace' }}>
                                {r.type === 'vip' && `${r.vip_ip}/${r.vip_cidr} on ${r.vip_interface}`}
                                {r.type === 'action' && (r.activate_cmd || '').substring(0, 60)}
                                {r.type === 'noop' && 'dummy resource'}
                              </TableCell>
                              <TableCell align="right">
                                <IconButton size="small" color="error" onClick={() => confirmDelete('resource', r.id, r.resource_id)}><DeleteIcon fontSize="small" /></IconButton>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>

                    {/* Checks */}
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                      <Typography variant="subtitle2" color="text.secondary">Health Checks</Typography>
                      <Button size="small" startIcon={<AddIcon />} sx={{ ml: 'auto' }} onClick={() => openCreateCheck(g.id)}>Add</Button>
                    </Box>
                    <TableContainer>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Name</TableCell>
                            <TableCell>Type</TableCell>
                            <TableCell>Target</TableCell>
                            <TableCell>Scope</TableCell>
                            <TableCell>Interval</TableCell>
                            <TableCell align="right"></TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {(g.checks || []).map(c => (
                            <TableRow key={c.id} hover>
                              <TableCell>{c.name}</TableCell>
                              <TableCell><Chip label={c.type} size="small" /></TableCell>
                              <TableCell sx={{ fontSize: 11, fontFamily: 'monospace', maxWidth: 250, overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.target}</TableCell>
                              <TableCell><Chip label={c.scope} size="small" color={c.scope === 'cluster' ? 'primary' : 'default'} variant="outlined" /></TableCell>
                              <TableCell>{c.interval_sec}s / {c.timeout_sec}s</TableCell>
                              <TableCell align="right">
                                <IconButton size="small" color="error" onClick={() => confirmDelete('check', c.id, c.name)}><DeleteIcon fontSize="small" /></IconButton>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  </AccordionDetails>
                </Accordion>
              ))}
            </>
          )}

          {/* ── Status Tab ── */}
          {tab === 2 && (
            <Card>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Node</TableCell>
                      <TableCell>Active Node</TableCell>
                      <TableCell>Coordinator</TableCell>
                      <TableCell>SDOWN</TableCell>
                      <TableCell>ODOWN</TableCell>
                      <TableCell>Self Healthy</TableCell>
                      <TableCell>Fail Count</TableCell>
                      <TableCell>Updated</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(clusterFull.status || []).map(s => (
                      <TableRow key={s.id} hover>
                        <TableCell><Chip label={s.node_id} size="small" icon={<DnsIcon />} /></TableCell>
                        <TableCell>{s.active_node || '—'}</TableCell>
                        <TableCell><Chip label={s.is_coordinator ? 'Yes' : 'No'} size="small" color={s.is_coordinator ? 'primary' : 'default'} /></TableCell>
                        <TableCell><Chip label={s.sdown ? 'SDOWN' : 'OK'} size="small" color={s.sdown ? 'error' : 'success'} /></TableCell>
                        <TableCell><Chip label={s.odown ? 'ODOWN' : 'OK'} size="small" color={s.odown ? 'error' : 'success'} /></TableCell>
                        <TableCell><Chip label={s.self_healthy ? 'Healthy' : 'Unhealthy'} size="small" color={s.self_healthy ? 'success' : 'error'} /></TableCell>
                        <TableCell>{s.fail_count}</TableCell>
                        <TableCell sx={{ fontSize: 12 }}>{s.updated_at}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Card>
          )}
        </>
      )}

      {/* ── Cluster Dialog ── */}
      <Dialog open={clusterDialog} onClose={() => setClusterDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{clusterMode === 'create' ? 'Create Cluster' : 'Edit Cluster'}</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <Grid container spacing={1}>
            <Grid item xs={6}><TextField label="Name" fullWidth size="small" margin="dense" value={clusterForm.name || ''} onChange={e => cf('name', e.target.value)} required /></Grid>
            <Grid item xs={6}><TextField label="Tenant" fullWidth size="small" margin="dense" value={clusterForm.tenant || ''} onChange={e => cf('tenant', e.target.value)} required /></Grid>
            <Grid item xs={4}><TextField label="Quorum" fullWidth size="small" margin="dense" type="number" value={clusterForm.quorum || 2} onChange={e => cf('quorum', +e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="Fail Threshold" fullWidth size="small" margin="dense" type="number" value={clusterForm.fail_threshold || 3} onChange={e => cf('fail_threshold', +e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="Check Interval (s)" fullWidth size="small" margin="dense" type="number" value={clusterForm.check_interval_sec || 5} onChange={e => cf('check_interval_sec', +e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="Max Failovers" fullWidth size="small" margin="dense" type="number" value={clusterForm.max_failovers || 3} onChange={e => cf('max_failovers', +e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="Failover Window (s)" fullWidth size="small" margin="dense" type="number" value={clusterForm.failover_window_sec || 3600} onChange={e => cf('failover_window_sec', +e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="Stale Obs (s)" fullWidth size="small" margin="dense" type="number" value={clusterForm.observation_stale_sec || 30} onChange={e => cf('observation_stale_sec', +e.target.value)} /></Grid>
            <Grid item xs={8}><TextField label="Consul Address" fullWidth size="small" margin="dense" value={clusterForm.consul_address || ''} onChange={e => cf('consul_address', e.target.value)} /></Grid>
            <Grid item xs={4}>
              <FormControlLabel control={<Switch checked={!!clusterForm.enabled} onChange={e => cf('enabled', e.target.checked)} />} label="Enabled" sx={{ mt: 1 }} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setClusterDialog(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={saveCluster}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* ── Node Dialog ── */}
      <Dialog open={nodeDialog} onClose={() => setNodeDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{nodeMode === 'create' ? 'Add Node' : 'Edit Node'}</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <Grid container spacing={1}>
            <Grid item xs={6}><TextField label="Node ID" fullWidth size="small" margin="dense" value={nodeForm.node_id || ''} onChange={e => nf('node_id', e.target.value)} required disabled={nodeMode === 'edit'} /></Grid>
            <Grid item xs={6}><TextField label="Address" fullWidth size="small" margin="dense" value={nodeForm.address || ''} onChange={e => nf('address', e.target.value)} required /></Grid>
            <Grid item xs={4}><TextField label="Priority" fullWidth size="small" margin="dense" type="number" value={nodeForm.priority || 1} onChange={e => nf('priority', +e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="SSH User" fullWidth size="small" margin="dense" value={nodeForm.ssh_user || 'root'} onChange={e => nf('ssh_user', e.target.value)} /></Grid>
            <Grid item xs={4}><TextField label="SSH Port" fullWidth size="small" margin="dense" type="number" value={nodeForm.ssh_port || 22} onChange={e => nf('ssh_port', +e.target.value)} /></Grid>
            <Grid item xs={12}><TextField label="Fence Command" fullWidth size="small" margin="dense" value={nodeForm.fence_cmd || ''} onChange={e => nf('fence_cmd', e.target.value)} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setNodeDialog(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={saveNode}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* ── Group Dialog ── */}
      <Dialog open={groupDialog} onClose={() => setGroupDialog(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Add Resource Group</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <TextField label="Group ID" fullWidth size="small" margin="dense" value={groupForm.group_id || ''} onChange={e => setGroupForm(p => ({ ...p, group_id: e.target.value }))} required helperText="e.g. sigtran-failover" />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setGroupDialog(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={saveGroup}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* ── Resource Dialog ── */}
      <Dialog open={resourceDialog} onClose={() => setResourceDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Resource</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <Grid container spacing={1}>
            <Grid item xs={6}><TextField label="Resource ID" fullWidth size="small" margin="dense" value={resourceForm.resource_id || ''} onChange={e => rf('resource_id', e.target.value)} required /></Grid>
            <Grid item xs={3}>
              <FormControl fullWidth size="small" margin="dense">
                <InputLabel>Type</InputLabel>
                <Select value={resourceForm.type || 'action'} label="Type" onChange={e => rf('type', e.target.value)}>
                  <MenuItem value="vip">VIP</MenuItem>
                  <MenuItem value="action">Action</MenuItem>
                  <MenuItem value="noop">Noop</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={3}><TextField label="Order" fullWidth size="small" margin="dense" type="number" value={resourceForm.sort_order || 0} onChange={e => rf('sort_order', +e.target.value)} /></Grid>
          </Grid>
          {resourceForm.type === 'vip' && (
            <Grid container spacing={1}>
              <Grid item xs={5}><TextField label="IP Address" fullWidth size="small" margin="dense" value={resourceForm.vip_ip || ''} onChange={e => rf('vip_ip', e.target.value)} required /></Grid>
              <Grid item xs={3}><TextField label="CIDR" fullWidth size="small" margin="dense" type="number" value={resourceForm.vip_cidr || 24} onChange={e => rf('vip_cidr', +e.target.value)} /></Grid>
              <Grid item xs={4}><TextField label="Interface" fullWidth size="small" margin="dense" value={resourceForm.vip_interface || 'eth0'} onChange={e => rf('vip_interface', e.target.value)} /></Grid>
            </Grid>
          )}
          {resourceForm.type === 'action' && (
            <>
              <TextField label="Activate Command" fullWidth size="small" margin="dense" value={resourceForm.activate_cmd || ''} onChange={e => rf('activate_cmd', e.target.value)} required multiline rows={2} />
              <TextField label="Deactivate Command" fullWidth size="small" margin="dense" value={resourceForm.deactivate_cmd || ''} onChange={e => rf('deactivate_cmd', e.target.value)} multiline rows={2} />
              <TextField label="Check Command" fullWidth size="small" margin="dense" value={resourceForm.check_cmd || ''} onChange={e => rf('check_cmd', e.target.value)} multiline rows={2} />
              <TextField label="Timeout (s)" size="small" margin="dense" type="number" value={resourceForm.cmd_timeout_sec || 30} onChange={e => rf('cmd_timeout_sec', +e.target.value)} sx={{ width: 120 }} />
            </>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setResourceDialog(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={saveResource}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* ── Check Dialog ── */}
      <Dialog open={checkDialog} onClose={() => setCheckDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add Health Check</DialogTitle>
        <DialogContent>
          {dialogError && <Alert severity="error" sx={{ mb: 1 }}>{dialogError}</Alert>}
          <Grid container spacing={1}>
            <Grid item xs={5}><TextField label="Name" fullWidth size="small" margin="dense" value={checkForm.name || ''} onChange={e => kf('name', e.target.value)} required /></Grid>
            <Grid item xs={3}>
              <FormControl fullWidth size="small" margin="dense">
                <InputLabel>Type</InputLabel>
                <Select value={checkForm.type || 'http'} label="Type" onChange={e => kf('type', e.target.value)}>
                  <MenuItem value="ping">Ping</MenuItem>
                  <MenuItem value="tcp">TCP</MenuItem>
                  <MenuItem value="http">HTTP</MenuItem>
                  <MenuItem value="script">Script</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={4}>
              <FormControl fullWidth size="small" margin="dense">
                <InputLabel>Scope</InputLabel>
                <Select value={checkForm.scope || 'cluster'} label="Scope" onChange={e => kf('scope', e.target.value)}>
                  <MenuItem value="cluster">Cluster</MenuItem>
                  <MenuItem value="self">Self</MenuItem>
                </Select>
              </FormControl>
            </Grid>
          </Grid>
          <TextField label="Target" fullWidth size="small" margin="dense" value={checkForm.target || ''} onChange={e => kf('target', e.target.value)} required helperText="URL for http, host:port for tcp, command for script" />
          <TextField label="Expected String" fullWidth size="small" margin="dense" value={checkForm.expect_str || ''} onChange={e => kf('expect_str', e.target.value)} helperText="Substring to match in response (optional)" />
          <Grid container spacing={1}>
            <Grid item xs={6}><TextField label="Interval (s)" fullWidth size="small" margin="dense" type="number" value={checkForm.interval_sec || 5} onChange={e => kf('interval_sec', +e.target.value)} /></Grid>
            <Grid item xs={6}><TextField label="Timeout (s)" fullWidth size="small" margin="dense" type="number" value={checkForm.timeout_sec || 3} onChange={e => kf('timeout_sec', +e.target.value)} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCheckDialog(false)} size="small">Cancel</Button>
          <Button variant="contained" size="small" onClick={saveCheck}>Save</Button>
        </DialogActions>
      </Dialog>

      {/* ── Delete Confirmation ── */}
      <Dialog open={deleteDialog.open} onClose={() => setDeleteDialog({ open: false, type: '', id: null, name: '' })}>
        <DialogTitle>Delete {deleteDialog.type}</DialogTitle>
        <DialogContent><Typography>Delete <strong>{deleteDialog.name}</strong>?</Typography></DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialog({ open: false, type: '', id: null, name: '' })}>Cancel</Button>
          <Button variant="contained" color="error" onClick={executeDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
