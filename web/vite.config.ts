import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'https://promise3042.up.railway.app',
        changeOrigin: true,
        secure: true,
      },
      '/ws': {
        target: 'wss://promise3042.up.railway.app',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})

