import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// The browser talks to one origin only: this dev/preview server. Requests under /api are
// forwarded to the Spring backend, so the SameSite=Strict auth cookies stay first-party and
// the browser's Origin header (http://localhost:5173) reaches the server unchanged.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const target = env.VITE_DEV_API_TARGET || 'http://localhost:8080'
  const proxy = { '/api': { target, changeOrigin: false } }
  return {
    plugins: [react()],
    // Kakao redirect URIs must match exactly, so never fall back to another port.
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 5173, strictPort: true, proxy },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
    },
  }
})
