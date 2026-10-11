import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the backend runs on :8080. The proxy means no CORS setup is needed locally.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  test: {
    environment: "node",
  },
});
