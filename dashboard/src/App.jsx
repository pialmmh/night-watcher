import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Layout from './components/Layout';
import Overview from './pages/Overview';
import Logs from './pages/Logs';
import Modules from './pages/Modules';
import SecurityEvents from './pages/SecurityEvents';
import Waf from './pages/Waf';
import Watchdog from './pages/Watchdog';
import Network from './pages/Network';
import HaCluster from './pages/HaCluster';
import Login from './pages/Login';
import Profile from './pages/Profile';
import UserManagement from './pages/UserManagement';
import GatewayOverview from './pages/GatewayOverview';
import GatewayPolicies from './pages/GatewayPolicies';
import GatewayAudit from './pages/GatewayAudit';
import KeycloakAdmin from './pages/KeycloakAdmin';
import RoleManagement from './pages/RoleManagement';
import SessionManagement from './pages/SessionManagement';
import LoginEvents from './pages/LoginEvents';
import RealmSettings from './pages/RealmSettings';
import ClientManagement from './pages/ClientManagement';

const theme = createTheme({
  palette: {
    primary: { main: '#1565c0' },
  },
  typography: {
    fontSize: 13,
  },
  components: {
    MuiTableCell: {
      styleOverrides: {
        root: { padding: '6px 12px' },
      },
    },
  },
});

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* Public route */}
            <Route path="/login" element={<Login />} />

            {/* Protected routes */}
            <Route path="/*" element={
              <ProtectedRoute>
                <Layout>
                  <Routes>
                    <Route path="/" element={<Overview />} />
                    <Route path="/logs" element={<Logs />} />
                    <Route path="/modules" element={<Modules />} />
                    <Route path="/security" element={<SecurityEvents />} />
                    <Route path="/waf" element={<Waf />} />
                    <Route path="/watchdog" element={<Watchdog />} />
                    <Route path="/network" element={<Network />} />
                    <Route path="/ha" element={<HaCluster />} />
                    <Route path="/profile" element={<Profile />} />
                    <Route path="/gateway" element={<GatewayOverview />} />
                    <Route path="/gateway/policies" element={<GatewayPolicies />} />
                    <Route path="/gateway/audit" element={<GatewayAudit />} />
                    <Route path="/gateway/keycloak" element={<KeycloakAdmin />} />
                    <Route path="/roles" element={
                      <ProtectedRoute requireAdmin><RoleManagement /></ProtectedRoute>
                    } />
                    <Route path="/sessions" element={
                      <ProtectedRoute requireAdmin><SessionManagement /></ProtectedRoute>
                    } />
                    <Route path="/events" element={
                      <ProtectedRoute requireAdmin><LoginEvents /></ProtectedRoute>
                    } />
                    <Route path="/realm" element={
                      <ProtectedRoute requireAdmin><RealmSettings /></ProtectedRoute>
                    } />
                    <Route path="/clients" element={
                      <ProtectedRoute requireAdmin><ClientManagement /></ProtectedRoute>
                    } />
                    <Route path="/users" element={
                      <ProtectedRoute requireAdmin>
                        <UserManagement />
                      </ProtectedRoute>
                    } />
                  </Routes>
                </Layout>
              </ProtectedRoute>
            } />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
