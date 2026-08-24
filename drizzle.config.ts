import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "drizzle-kit";

// drizzle-kit no carga .env.local automáticamente; lo leemos aquí para que
// `db:push` y `db:generate` usen la misma configuración que los scripts.
for (const filename of [".env", ".env.local"]) {
  let content: string;
  try {
    content = readFileSync(resolve(process.cwd(), filename), "utf8");
  } catch {
    continue;
  }
  for (const line of content.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const match = /^([A-Z0-9_]+)\s*=\s*(.*)$/.exec(trimmed);
    if (!match) continue;
    const [, key, raw] = match;
    const value = raw.replace(/^["'](.*)["']$/, "$1");
    if (!(key in process.env)) process.env[key] = value;
  }
}

export default defineConfig({
  schema: "./src/server/db/schema.ts",
  out: "./drizzle",
  dialect: "postgresql",
  dbCredentials: {
    url: process.env.DATABASE_URL ?? "",
  },
});
