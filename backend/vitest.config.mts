import path from "node:path";
import { defineConfig } from "vitest/config";

const alias = {
  "@": path.resolve(import.meta.dirname, "../src"),
};

// Pruebas del backend HTTP (capa Hono): autenticación, CORS y contratos de
// las rutas, con repositorios simulados en memoria (sin base de datos).
export default defineConfig({
  resolve: { alias },
  test: {
    name: "backend",
    environment: "node",
    include: ["backend/tests/**/*.test.ts"],
  },
});