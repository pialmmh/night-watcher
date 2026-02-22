import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import Layout from './components/Layout';
import Overview from './pages/Overview';
import Logs from './pages/Logs';
import Modules from './pages/Modules';
import SecurityEvents from './pages/SecurityEvents';
import Waf from './pages/Waf';
import Watchdog from './pages/Watchdog';
import Network from './pages/Network';
import HaCluster from './pages/HaCluster';

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
      <BrowserRouter>
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
          </Routes>
        </Layout>
      </BrowserRouter>
    </ThemeProvider>
  );
}
