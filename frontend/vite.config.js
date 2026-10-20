import { defineConfig } from 'vite';

// In development the Spring Boot backend runs on :8080; proxying keeps the browser on one origin.
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.BACKEND_URL ?? 'http://localhost:8080',
    },
  },
});
