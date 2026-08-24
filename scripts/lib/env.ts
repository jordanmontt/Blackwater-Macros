import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Carga variables de `.env.local` (y `.env`) para los scripts que se ejecutan
 * fuera del runtime de Next.js.
 */
export function loadEnvFiles(): void {
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
}
