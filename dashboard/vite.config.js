import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 7100,
    proxy: {
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
