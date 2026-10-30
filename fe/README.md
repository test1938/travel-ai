# TravelPilot AI - frontend

React 19 + TypeScript + Vite 8, TanStack Query, React Router, Tailwind CSS 4.

```bash
npm install
npm run dev          # http://localhost:5173, /api is proxied to the backend on :8080
npm run lint         # oxlint
npm run format       # prettier
npm run build
```

Layout: `src/api` (typed fetch client), `src/types` (API types), `src/features/<feature>` (components + hooks), `src/components` (shared UI).
