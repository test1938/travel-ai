import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The backend runs on :8088 (SERVER_PORT); the browser only talks to the Vite dev server.
// Point the proxy elsewhere with BACKEND_URL, e.g. BACKEND_URL=http://localhost:9090 npm run dev.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8088'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': backend,
      '/actuator': backend,
    },
  },
})
