import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";

function createDb() {
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) {
    throw new Error(
      "DATABASE_URL no está definida. Crea un archivo .env.local con la cadena de conexión de tu base de datos.",
    );
  }

  const isProd = process.env.NODE_ENV === "production";
  const client = postgres(connectionString, {
    max: isProd ? 5 : 1,
    ssl: connectionString.includes("localhost") || connectionString.includes("127.0.0.1")
      ? false
      : "require",
  });

  return drizzle(client, { schema });
}

export type AppDb = ReturnType<typeof createDb>;

let instance: AppDb | null = null;

/** Crea la conexión real solo en la primera consulta (o llamada explícita). */
export function getDb(): AppDb {
  instance ??= createDb();
  return instance;
}

/**
 * Proxy perezoso: importar `db` no abre conexión ni exige DATABASE_URL
 * en tiempo de build; el error aparece al ejecutar la primera query.
 */
export const db: AppDb = new Proxy(Object.create(null), {
  get(_target, prop, receiver) {
    return Reflect.get(getDb(), prop, receiver);
  },
}) as AppDb;

export { schema };
