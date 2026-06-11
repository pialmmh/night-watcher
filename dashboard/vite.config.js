import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import devMockApi from './dev-mock-api.js';

const useMock = process.env.VITE_MOCK !== 'false';

export default defineConfig({
  plugins: [react(), ...(useMock ? [devMockApi()] : [])],
  server: {
    port: 7100,
    proxy: {
      // Always proxy HA cluster API to Python backend
      '/api/clusters': { target: 'http://127.0.0.1:7105', changeOrigin: true },
      '/api/nodes': { target: 'http://127.0.0.1:7105', changeOrigin: true },
      '/api/groups': { target: 'http://127.0.0.1:7105', changeOrigin: true },
      '/api/resources': { target: 'http://127.0.0.1:7105', changeOrigin: true },
      '/api/checks': { target: 'http://127.0.0.1:7105', changeOrigin: true },
      // Keycloak proxy — strip /auth prefix (Keycloak runs at root in dev, nginx adds /auth in prod)
      '/auth': { target: 'http://127.0.0.1:7104', changeOrigin: true, rewrite: (path) => path.replace(/^\/auth/, '') },
      ...(useMock ? {} : {
      '/api/es': {
        target: 'https://10.10.195.1:9200',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api\/es/, ''),
        headers: { Authorization: 'Basic YWRtaW46YWRtaW4=' },
      },
      '/api/wazuh': {
        target: 'https://10.10.195.1:55000',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api\/wazuh/, ''),
      },
      '/api/hactl/node1': {
        target: 'http://127.0.0.1:7102',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/hactl\/node1/, ''),
      },
      '/api/hactl/node2': {
        target: 'http://127.0.0.1:7103',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/hactl\/node2/, ''),
      },
      '/api/hactl/node3': {
        target: 'http://127.0.0.1:7104',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/hactl\/node3/, ''),
      },
      '/api/hactl': {
        target: 'http://127.0.0.1:7102',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/hactl/, ''),
      },
    }),
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@mui/icons-material'],
          charts: ['recharts'],
        },
      },
    },
  },
});
