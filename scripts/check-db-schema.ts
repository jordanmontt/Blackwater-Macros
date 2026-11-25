/**
 * Fails when the database lacks a table, column or enum value the code uses
 * (src/server/db/schema-check.ts). Runs before every `npm run build` (prebuild):
 *
 * - on Vercel, a failure stops the deploy, so the version already online keeps running;
 * - locally, with DATABASE_URL in .env.local (the shared database), it reminds you to run
 *   `npm run db:push` before pushing; an unreachable database only warns, so builds work offline;
 * - without DATABASE_URL (CI, Vercel previews) it is skipped.
 *
 * By hand: `npm run db:check`.
 */
import postgres from "postgres";
import { loadEnvFiles } from "./lib/env";

// Loads .env.local BEFORE importing the modules that read DATABASE_URL.
loadEnvFiles();

async function main() {
  const url = process.env.DATABASE_URL;
  if (!url) {
    console.log("Database check skipped: no DATABASE_URL.");
    return;
  }
  // Vercel sets VERCEL=1 during its builds.
  const strict = Boolean(process.env.VERCEL);

  const [schema, { expectedSchema, schemaProblems }, { postgresSsl }] = await Promise.all([
    import("../src/server/db/schema"),
    import("../src/server/db/schema-check"),
    import("../src/server/db/client"),
  ]);

  const sql = postgres(url, { max: 1, connect_timeout: 20, ssl: postgresSsl(url), onnotice: () => {} });
  let columns;
  let enumValues;
  try {
    columns = await sql<{ table: string; column: string; required: boolean }[]>`
      select table_name as "table", column_name as "column",
             (is_nullable = 'NO' and column_default is null
              and is_identity = 'NO' and is_generated = 'NEVER') as "required"
      from information_schema.columns
      where table_schema = current_schema()`;
    enumValues = await sql<{ enumName: string; value: string }[]>`
      select t.typname as "enumName", e.enumlabel as "value"
      from pg_type t
      join pg_enum e on e.enumtypid = t.oid
      join pg_namespace n on n.oid = t.typnamespace
      where n.nspname = current_schema()`;
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    if (strict) {
      console.error(`✘ Could not read the database to check it against the code: ${reason}`);
      process.exit(1);
    }
    console.warn(`Database check skipped: could not reach the database (${reason}).`);
    return;
  } finally {
    await sql.end({ timeout: 5 }).catch(() => {});
  }

  const problems = schemaProblems(expectedSchema(schema), columns, enumValues);
  if (problems.length > 0) {
    console.error("✘ The database does not match the code (src/server/db/schema.ts):");
    for (const problem of problems) console.error(`  - ${problem}`);
    console.error("Run `DATABASE_URL=… npm run db:push`, then build (deploy) again.");
    if (strict) console.error("This version did not go live: the one already online keeps running.");
    process.exit(1);
  }
  console.log("✔ The database matches the code.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
