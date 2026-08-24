import path from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const alias = {
  "@": path.resolve(import.meta.dirname, "./src"),
};

// Dos familias de pruebas bien separadas:
// - tests/behavior : requisitos de usuario, caja negra ("¿qué hace?")
// - tests/unit     : detalles técnicos de piezas puras ("¿cómo lo hace?")
export default defineConfig({
  test: {
    projects: [
      {
        resolve: { alias },
        test: {
          name: "unit",
          include: ["tests/unit/**/*.test.ts"],
          environment: "node",
        },
      },
      {
        resolve: { alias },
        test: {
          name: "behavior",
          include: ["tests/behavior/**/*.test.ts"],
          environment: "node",
        },
      },
      {
        plugins: [react()],
        resolve: { alias },
        test: {
          name: "behavior-ui",
          include: ["tests/behavior/**/*.test.tsx"],
          environment: "happy-dom",
          setupFiles: ["./tests/setup-ui.ts"],
        },
      },
    ],
  },
});
