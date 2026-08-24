import { loadEnvFiles } from "./lib/env";

// Carga .env.local ANTES de importar los módulos que leen DATABASE_URL.
loadEnvFiles();

const [username, password] = process.argv.slice(2);

if (!username || !password) {
  console.error("Uso: npm run create-user -- <usuario> <contraseña>");
  console.error("Ejemplo: npm run create-user -- sebastian mi-clave-secreta");
  process.exit(1);
}

async function main() {
  const [{ hashPassword }, { repositories }, { db }, { users }, { eq }] = await Promise.all([
    import("../src/server/auth/password"),
    import("../src/server/composition"),
    import("../src/server/db/client"),
    import("../src/server/db/schema"),
    import("drizzle-orm"),
  ]);

  const existing = await repositories.users.findByUsername(username);
  const passwordHash = await hashPassword(password);

  if (existing) {
    await db.update(users).set({ passwordHash }).where(eq(users.id, existing.id));
    console.log(`✔ Contraseña actualizada para el usuario "${existing.username}" (${existing.id})`);
  } else {
    const created = await repositories.users.create({ username, passwordHash });
    console.log(`✔ Usuario creado: ${created.username} (id: ${created.id})`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("✘ Error:", error.message);
    process.exit(1);
  });
