import { loadEnvFiles } from "./lib/env";

// Carga .env.local ANTES de importar los módulos que leen DATABASE_URL.
loadEnvFiles();

const [username] = process.argv.slice(2);

if (!username) {
  console.error("Uso: npm run set-admin -- <usuario>");
  console.error("Ejemplo: npm run set-admin -- jordanmontt");
  process.exit(1);
}

async function main() {
  const [{ repositories }, { db }, { users }, { eq }] = await Promise.all([
    import("../src/server/composition"),
    import("../src/server/db/client"),
    import("../src/server/db/schema"),
    import("drizzle-orm"),
  ]);

  const existing = await repositories.users.findByUsername(username);
  if (!existing) {
    console.error(`✘ No existe ningún usuario llamado "${username}".`);
    process.exit(1);
  }
  await db.update(users).set({ isAdmin: true }).where(eq(users.id, existing.id));
  console.log(`✔ ${existing.username} ahora es administrador.`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("✘ Error:", error.message);
    process.exit(1);
  });