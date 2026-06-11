import { useState, useEffect, useCallback } from 'react';
import {
  Box, Typography, Button, Table, TableHead, TableRow, TableCell, TableBody,
  IconButton, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  Chip, CircularProgress, Alert, Collapse, Tooltip, Stack,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import GroupIcon from '@mui/icons-material/Group';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import FolderIcon from '@mui/icons-material/Folder';
import FolderOpenIcon from '@mui/icons-material/FolderOpen';
import {
  listGroups, createGroup, createSubGroup, updateGroup, deleteGroup,
  getGroupMembers, listUsers, addUserToGroup, removeUserFromGroup,
} from '../auth/keycloak';
import { useAuth } from '../auth/AuthContext';

export default function GroupManagement() {
  const { accessToken: token } = useAuth();
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedGroups, setExpandedGroups] = useState({});
  const [membersMap, setMembersMap] = useState({});
  const [loadingMembers, setLoadingMembers] = useState({});

  // dialogs
  const [groupDialog, setGroupDialog] = useState({ open: false, parent: null, edit: null });
  const [groupName, setGroupName] = useState('');
  const [savingGroup, setSavingGroup] = useState(false);
  const [groupError, setGroupError] = useState('');

  const [memberDialog, setMemberDialog] = useState({ open: false, group: null });
  const [allUsers, setAllUsers] = useState([]);
  const [userSearch, setUserSearch] = useState('');
  const [savingMember, setSavingMember] = useState('');

  const [deleteDialog, setDeleteDialog] = useState({ open: false, group: null });

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setGroups(await listGroups(token));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const toggleExpand = async (group) => {
    const id = group.id;
    const nowOpen = !expandedGroups[id];
    setExpandedGroups(prev => ({ ...prev, [id]: nowOpen }));
    if (nowOpen && !membersMap[id]) {
      setLoadingMembers(prev => ({ ...prev, [id]: true }));
      try {
        const members = await getGroupMembers(token, id);
        setMembersMap(prev => ({ ...prev, [id]: members }));
      } catch {
        setMembersMap(prev => ({ ...prev, [id]: [] }));
      } finally {
        setLoadingMembers(prev => ({ ...prev, [id]: false }));
      }
    }
  };

  const openCreateDialog = (parent = null) => {
    setGroupName('');
    setGroupError('');
    setGroupDialog({ open: true, parent, edit: null });
  };

  const openEditDialog = (group) => {
    setGroupName(group.name);
    setGroupError('');
    setGroupDialog({ open: true, parent: null, edit: group });
  };

  const saveGroup = async () => {
    if (!groupName.trim()) { setGroupError('Name is required'); return; }
    setSavingGroup(true);
    setGroupError('');
    try {
      const { edit, parent } = groupDialog;
      if (edit) {
        await updateGroup(token, edit.id, { name: groupName.trim() });
      } else if (parent) {
        await createSubGroup(token, parent.id, { name: groupName.trim() });
      } else {
        await createGroup(token, { name: groupName.trim() });
      }
      setGroupDialog({ open: false, parent: null, edit: null });
      await load();
    } catch (e) {
      setGroupError(e.message);
    } finally {
      setSavingGroup(false);
    }
  };

  const confirmDelete = async () => {
    const { group } = deleteDialog;
    try {
      await deleteGroup(token, group.id);
      setDeleteDialog({ open: false, group: null });
      await load();
    } catch (e) {
      setError(e.message);
    }
  };

  const openMemberDialog = async (group) => {
    setMemberDialog({ open: true, group });
    setUserSearch('');
    try {
      const users = await listUsers(token, '', 0, 100);
      setAllUsers(users);
      if (!membersMap[group.id]) {
        const members = await getGroupMembers(token, group.id);
        setMembersMap(prev => ({ ...prev, [group.id]: members }));
      }
    } catch (e) {
      setError(e.message);
    }
  };

  const toggleMember = async (user) => {
    const { group } = memberDialog;
    const members = membersMap[group.id] || [];
    const isMember = members.some(m => m.id === user.id);
    setSavingMember(user.id);
    try {
      if (isMember) {
        await removeUserFromGroup(token, user.id, group.id);
        setMembersMap(prev => ({ ...prev, [group.id]: prev[group.id].filter(m => m.id !== user.id) }));
      } else {
        await addUserToGroup(token, user.id, group.id);
        setMembersMap(prev => ({ ...prev, [group.id]: [...(prev[group.id] || []), user] }));
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setSavingMember('');
    }
  };

  const filteredUsers = allUsers.filter(u =>
    !userSearch || u.username?.toLowerCase().includes(userSearch.toLowerCase()) ||
    u.email?.toLowerCase().includes(userSearch.toLowerCase())
  );

  const renderGroup = (group, depth = 0) => {
    const expanded = expandedGroups[group.id];
    const members = membersMap[group.id] || [];
    const loadingM = loadingMembers[group.id];
    const hasSubGroups = group.subGroups?.length > 0;

    return (
      <Box key={group.id}>
        <TableRow hover sx={{ '& td': { pl: depth > 0 ? `${16 + depth * 24}px` : 2 } }}>
          <TableCell>
            <Stack direction="row" alignItems="center" spacing={1}>
              {hasSubGroups || depth === 0
                ? (expanded ? <FolderOpenIcon fontSize="small" color="primary" /> : <FolderIcon fontSize="small" color="action" />)
                : <Box sx={{ width: 20 }} />}
              <Typography variant="body2" fontWeight={depth === 0 ? 600 : 400}>
                {group.name}
              </Typography>
            </Stack>
          </TableCell>
          <TableCell>
            <Typography variant="caption" color="text.secondary">{group.path}</Typography>
          </TableCell>
          <TableCell align="right">
            {expanded && members.length > 0 && (
              <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end">
                {members.slice(0, 3).map(m => (
                  <Chip key={m.id} label={m.username} size="small" />
                ))}
                {members.length > 3 && <Chip label={`+${members.length - 3}`} size="small" variant="outlined" />}
              </Stack>
            )}
          </TableCell>
          <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
            <Tooltip title="Manage members">
              <IconButton size="small" onClick={() => openMemberDialog(group)}>
                <PersonAddIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Add sub-group">
              <IconButton size="small" onClick={() => openCreateDialog(group)}>
                <AddIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Rename">
              <IconButton size="small" onClick={() => openEditDialog(group)}>
                <EditIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Expand members">
              <IconButton size="small" onClick={() => toggleExpand(group)}>
                {loadingM ? <CircularProgress size={14} /> : expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Delete">
              <IconButton size="small" color="error" onClick={() => setDeleteDialog({ open: true, group })}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </TableCell>
        </TableRow>
        {expanded && hasSubGroups && group.subGroups.map(sg => renderGroup(sg, depth + 1))}
      </Box>
    );
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h6" fontWeight={600}>Groups</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage tenant groups and membership. Tenant groups follow the <code>/tenants/&#123;slug&#125;</code> convention.
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} size="small" onClick={() => openCreateDialog()}>
          New Group
        </Button>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell><strong>Name</strong></TableCell>
              <TableCell><strong>Path</strong></TableCell>
              <TableCell align="right"><strong>Members</strong></TableCell>
              <TableCell align="right"><strong>Actions</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {groups.length === 0 ? (
              <TableRow>
                <TableCell colSpan={4} align="center">
                  <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>
                    No groups yet. Create a <code>/tenants</code> group first, then add tenant sub-groups.
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              groups.map(g => renderGroup(g))
            )}
          </TableBody>
        </Table>
      )}

      {/* Create / Edit Group Dialog */}
      <Dialog open={groupDialog.open} onClose={() => setGroupDialog({ open: false, parent: null, edit: null })} maxWidth="xs" fullWidth>
        <DialogTitle>
          {groupDialog.edit ? 'Rename Group' : groupDialog.parent ? `New Sub-group under "${groupDialog.parent?.name}"` : 'New Group'}
        </DialogTitle>
        <DialogContent sx={{ pb: 1, overflow: 'visible' }}>
          {groupError && <Alert severity="error" sx={{ mb: 1.5 }}>{groupError}</Alert>}
          <TextField
            label="Group Name"
            value={groupName}
            onChange={e => setGroupName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && saveGroup()}
            fullWidth autoFocus
            sx={{ mt: 1 }}
            helperText={!groupDialog.parent && !groupDialog.edit ? 'e.g. "tenants" or "btcl"' : ''}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setGroupDialog({ open: false, parent: null, edit: null })}>Cancel</Button>
          <Button variant="contained" onClick={saveGroup} disabled={savingGroup}>
            {savingGroup ? <CircularProgress size={18} /> : groupDialog.edit ? 'Save' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Manage Members Dialog */}
      <Dialog open={memberDialog.open} onClose={() => setMemberDialog({ open: false, group: null })} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Stack direction="row" alignItems="center" spacing={1}>
            <GroupIcon />
            <span>Members — <code>{memberDialog.group?.path}</code></span>
          </Stack>
        </DialogTitle>
        <DialogContent sx={{ pt: 1 }}>
          <TextField
            label="Search users"
            value={userSearch}
            onChange={e => setUserSearch(e.target.value)}
            fullWidth size="small" sx={{ mb: 1.5 }}
          />
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Username</TableCell>
                <TableCell>Email</TableCell>
                <TableCell align="right">In Group</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredUsers.map(u => {
                const isMember = (membersMap[memberDialog.group?.id] || []).some(m => m.id === u.id);
                return (
                  <TableRow key={u.id} hover>
                    <TableCell>{u.username}</TableCell>
                    <TableCell>{u.email || '—'}</TableCell>
                    <TableCell align="right">
                      {savingMember === u.id ? (
                        <CircularProgress size={16} />
                      ) : isMember ? (
                        <Tooltip title="Remove from group">
                          <IconButton size="small" color="error" onClick={() => toggleMember(u)}>
                            <PersonRemoveIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      ) : (
                        <Tooltip title="Add to group">
                          <IconButton size="small" color="primary" onClick={() => toggleMember(u)}>
                            <PersonAddIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setMemberDialog({ open: false, group: null })}>Close</Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirm */}
      <Dialog open={deleteDialog.open} onClose={() => setDeleteDialog({ open: false, group: null })} maxWidth="xs">
        <DialogTitle>Delete Group</DialogTitle>
        <DialogContent>
          <Typography>Delete <strong>{deleteDialog.group?.name}</strong>? This removes the group and all membership assignments.</Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setDeleteDialog({ open: false, group: null })}>Cancel</Button>
          <Button variant="contained" color="error" onClick={confirmDelete}>Delete</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
