import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  AppBar, Box, CssBaseline, Drawer, IconButton, List, ListItemButton,
  ListItemIcon, ListItemText, Toolbar, Typography, Divider,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import DashboardIcon from '@mui/icons-material/Dashboard';
import SecurityIcon from '@mui/icons-material/Security';
import ShieldIcon from '@mui/icons-material/Shield';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import PublicIcon from '@mui/icons-material/Public';
import StorageIcon from '@mui/icons-material/Storage';
import ExtensionIcon from '@mui/icons-material/Extension';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';

const DRAWER_WIDTH = 220;

const NAV_ITEMS = [
  { label: 'Overview', path: '/', icon: <DashboardIcon /> },
  { label: 'Modules', path: '/modules', icon: <ExtensionIcon /> },
  { label: 'Log Explorer', path: '/logs', icon: <StorageIcon /> },
  { label: 'Security Events', path: '/security', icon: <SecurityIcon /> },
  { label: 'WAF', path: '/waf', icon: <ShieldIcon /> },
  { label: 'Watchdog', path: '/watchdog', icon: <MonitorHeartIcon /> },
  { label: 'Network', path: '/network', icon: <PublicIcon /> },
  { label: 'HA Cluster', path: '/ha', icon: <DeviceHubIcon /> },
];

export default function Layout({ children }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const drawer = (
    <Box>
      <Toolbar>
        <ShieldIcon sx={{ mr: 1, color: 'primary.main' }} />
        <Typography variant="subtitle1" noWrap fontWeight={600}>
          SecDash
        </Typography>
      </Toolbar>
      <Divider />
      <List>
        {NAV_ITEMS.map((item) => (
          <ListItemButton
            key={item.path}
            selected={location.pathname === item.path}
            onClick={() => { navigate(item.path); setMobileOpen(false); }}
          >
            <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex' }}>
      <CssBaseline />
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }} elevation={1}>
        <Toolbar variant="dense">
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setMobileOpen(!mobileOpen)}
            sx={{ mr: 2, display: { md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <ShieldIcon sx={{ mr: 1 }} />
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            Security Dashboard
          </Typography>
        </Toolbar>
      </AppBar>

      {/* Mobile drawer */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH },
        }}
      >
        {drawer}
      </Drawer>

      {/* Desktop drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
        open
      >
        {drawer}
      </Drawer>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 2,
          mt: 6,
          ml: { md: `${DRAWER_WIDTH}px` },
          minHeight: '100vh',
          bgcolor: 'grey.50',
        }}
      >
        {children}
      </Box>
    </Box>
  );
}
