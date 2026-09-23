import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), '')
  const shouldChangeProxyOrigin = environment.VITE_API_PROXY_CHANGE_ORIGIN !== 'false'
  const allowedHosts = environment.VITE_ALLOWED_HOSTS?.split(',').map((host) => host.trim()).filter(Boolean)

  return {
    plugins: [react()],
    server: {
      ...(allowedHosts?.length ? { allowedHosts } : {}),
      proxy: {
        '/api': {
          target: environment.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: shouldChangeProxyOrigin,
        },
        '/auth': {
          target: environment.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: shouldChangeProxyOrigin,
        },
      },
    },
  }
})
