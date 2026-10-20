import { defineConfig } from 'vite';

// In development the Spring Boot backend runs on :8080; proxying keeps the browser on one origin.
// base './' lets the same build work at a GitHub Pages project path (/Dublin-Bus-Live/).
export default defineConfig({
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.BACKEND_URL ?? 'http://localhost:8080',
    },
  },
});
