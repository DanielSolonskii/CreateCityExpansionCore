import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  const target = process.env.VITE_ACCOUNT_API_BASE_URL ?? env.VITE_ACCOUNT_API_BASE_URL;
  return {
  plugins: [react()],
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    watch: { ignored: ["**/src-tauri/**"] },
    proxy: target ? { "/account-api": { target, changeOrigin: true, rewrite: path => path.replace(/^\/account-api/, "") } } : undefined,
  },
  envPrefix: ["VITE_", "TAURI_ENV_*"],
  };
});
