import { loadEnvFiles } from "../../scripts/lib/env";

// Carga .env.local ANTES de importar los módulos que leen variables.
loadEnvFiles();

const { prodDeps } = await import("./deps");
const { createApp } = await import("./app");

const { serve } = await import("@hono/node-server");

const app = createApp(prodDeps);
const port = Number(process.env.PORT ?? 8787);

serve({ fetch: app.fetch, port }, (info) => {
  console.log(`API lista en http://localhost:${info.port}`);
});