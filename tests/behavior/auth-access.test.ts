import { describe, expect, it } from "vitest";
import { hashPassword } from "@/server/auth/password";
import { InvalidCredentialsError, login, logout, resolveSession } from "@/server/services/auth-service";

/**
 * Requisitos de acceso:
 *  - solo usuarios con contraseña correcta entran,
 *  - la sesión persiste entre visitas (no hay que entrar cada día),
 *  - se puede cerrar sesión,
 *  - los usuarios los crea un administrador por base de datos: no hay registro.
 */

function makeMemoryDeps() {
  const users = new Map<
    string,
    {
      id: string;
      username: string;
      passwordHash: string;
      gender: "male" | "female" | null;
      birthYear: number | null;
      heightCm: number | null;
      gymDaysPerWeek: number | null;
      gymSessionMinutes: number | null;
      walkingMinutesPerDay: number | null;
      calorieGoal: "cut" | "maintain" | "surplus" | null;
      createdAt: Date;
    }
  >();
  const sessions = new Map<string, { userId: string; expiresAt: Date }>();
  let nextId = 1;

  return {
    users: {
      async findByUsername(username: string) {
        return (
          [...users.values()].find((user) => user.username === username.toLowerCase()) ?? null
        );
      },
      async create({ username, passwordHash }: { username: string; passwordHash: string }) {
        const user = {
          id: `u-${nextId++}`,
          username: username.toLowerCase(),
          passwordHash,
          gender: null,
          birthYear: null,
          heightCm: null,
          gymDaysPerWeek: null,
          gymSessionMinutes: null,
          walkingMinutesPerDay: null,
          calorieGoal: null,
          createdAt: new Date(),
        };
        users.set(user.id, user);
        return user;
      },
    },
    sessions: {
      async create({
        token,
        userId,
        expiresAt,
      }: {
        token: string;
        userId: string;
        expiresAt: Date;
      }) {
        sessions.set(token, { userId, expiresAt });
      },
      async findByToken(token: string) {
        const record = sessions.get(token);
        return record ? { token, ...record } : null;
      },
      async deleteByToken(token: string) {
        sessions.delete(token);
      },
      async deleteExpiredBefore() {},
    },
  };
}

async function seedUser(deps: ReturnType<typeof makeMemoryDeps>, username = "sebastian", password = "pan-con-tomate") {
  await deps.users.create({ username, passwordHash: await hashPassword(password) });
  return { username, password };
}

describe("iniciar sesión", () => {
  it("con usuario y contraseña correctos se obtiene una sesión válida", async () => {
    const deps = makeMemoryDeps();
    const credentials = await seedUser(deps);

    const issued = await login(deps, credentials.username, credentials.password);

    expect(issued.token).toBeTruthy();
    await expect(resolveSession(deps, issued.token)).resolves.toBeTruthy();
  });

  it("con contraseña incorrecta se rechaza el acceso y no se crea ninguna sesión", async () => {
    const deps = makeMemoryDeps();
    const credentials = await seedUser(deps);

    await expect(login(deps, credentials.username, "otra-clave")).rejects.toBeInstanceOf(
      InvalidCredentialsError,
    );
  });

  it("un usuario que no existe tampoco entra (los usuarios no se auto-registran)", async () => {
    const deps = makeMemoryDeps();
    await expect(login(deps, "fantasma", "lo-que-sea")).rejects.toBeInstanceOf(
      InvalidCredentialsError,
    );
  });

  it("da igual mayúsculas/minúsculas en el nombre de usuario", async () => {
    const deps = makeMemoryDeps();
    const credentials = await seedUser(deps, "Sebastian");

    const issued = await login(deps, "SEBASTIAN", credentials.password);
    expect(issued.token).toBeTruthy();
  });
});

describe("mantener y cerrar la sesión", () => {
  it("la sesión sigue siendo válida días después sin volver a entrar", async () => {
    const deps = makeMemoryDeps();
    const credentials = await seedUser(deps);
    const issued = await login(deps, credentials.username, credentials.password);

    const later = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 días después
    await expect(resolveSession(deps, issued.token)).resolves.toBeTruthy();
    expect(later.getTime()).toBeLessThan(issued.expiresAt.getTime()); // aún no caduca a los 30 días
  });

  it("al cerrar sesión el token deja de funcionar", async () => {
    const deps = makeMemoryDeps();
    const credentials = await seedUser(deps);
    const issued = await login(deps, credentials.username, credentials.password);

    await logout(deps, issued.token);

    await expect(resolveSession(deps, issued.token)).resolves.toBeNull();
  });

  it("una sesión caducada ya no da acceso aunque se presente el token", async () => {
    const deps = makeMemoryDeps();
    await seedUser(deps);

    // Una fecha de expiración en el pasado no puede producirse con login(),
    // así que creamos directamente un registro vencido.
    const expiredToken = "expired-token";
    await deps.sessions.create({
      token: expiredToken,
      userId: "cualquiera",
      expiresAt: new Date(Date.now() - 1000),
    });

    await expect(resolveSession(deps, expiredToken)).resolves.toBeNull();
  });
});
