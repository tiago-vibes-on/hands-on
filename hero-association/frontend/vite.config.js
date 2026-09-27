import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), '')
  const shouldChangeProxyOrigin = environment.VITE_API_PROXY_CHANGE_ORIGIN !== 'false'
  const configuredAllowedHosts = environment.VITE_ALLOWED_HOSTS?.split(',').map((host) => host.trim()).filter(Boolean) || []
  const allowedHosts = [...new Set(['heroassociation.test', ...configuredAllowedHosts])]

  return {
    plugins: [react()],
    server: {
      allowedHosts,
      proxy: {
        '/api': {
          target: environment.VITE_API_PROXY_TARGET || 'http://localhost:17080',
          changeOrigin: shouldChangeProxyOrigin,
        },
        '/auth': {
          target: environment.VITE_API_PROXY_TARGET || 'http://localhost:17080',
          changeOrigin: shouldChangeProxyOrigin,
        },
      },
    },
  }
})
