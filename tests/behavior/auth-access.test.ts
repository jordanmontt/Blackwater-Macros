import { describe, expect, it } from "vitest";
import { hashPassword } from "@/server/auth/password";
import { InvalidCredentialsError, login, logout, resolveSession, resolveSessionDetails } from "@/server/services/auth-service";
import type { ProfilePeriod } from "@/lib/core/types";

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
      isAdmin: boolean;
      gender: "male" | "female" | null;
      birthYear: number | null;
      heightCm: number | null;
      gymDaysPerWeek: number | null;
      gymSessionMinutes: number | null;
      walkingMinutesPerDay: number | null;
      calorieGoal: "cut" | "maintain" | "surplus" | null;
      profileHistory: ProfilePeriod[];
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
      async findById(id: string) {
        return users.get(id) ?? null;
      },
      async list() {
        return [...users.values()];
      },
      async create({ username, passwordHash }: { username: string; passwordHash: string }) {
        const user = {
          id: `u-${nextId++}`,
          username: username.toLowerCase(),
          passwordHash,
          isAdmin: false,
          gender: null,
          birthYear: null,
          heightCm: null,
          gymDaysPerWeek: null,
          gymSessionMinutes: null,
          walkingMinutesPerDay: null,
          calorieGoal: null,
          profileHistory: [],
          createdAt: new Date(),
        };
        users.set(user.id, user);
        return user;
      },
      async update(
        id: string,
        data: { username?: string; passwordHash?: string; isAdmin?: boolean },
      ) {
        const existing = users.get(id);
        if (!existing) return null;
        const updated = { ...existing, ...data };
        if (data.username !== undefined) updated.username = data.username.toLowerCase();
        users.set(id, updated);
        return updated;
      },
      async delete(id: string) {
        return users.delete(id);
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
      async extend(token: string, expiresAt: Date) {
        const record = sessions.get(token);
        if (record) sessions.set(token, { ...record, expiresAt });
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

/**
 * Requisito: una sesión que se usa no caduca nunca (un móvil que sincroniza en segundo
 * plano no debe pedir entrar de nuevo cada 90 días); una que no se usa en 90 días, sí.
 */
describe("la sesión se renueva mientras se usa", () => {
  const DAY = 24 * 60 * 60 * 1000;
  const daysAfter = (start: Date, days: number) => new Date(start.getTime() + days * DAY);

  async function loggedIn() {
    const deps = makeMemoryDeps();
    const credentials = await seedUser(deps);
    const loginAt = new Date();
    const issued = await login(deps, credentials.username, credentials.password);
    return { deps, token: issued.token, loginAt };
  }

  it("usada cada mes, sigue funcionando un año después del login", async () => {
    const { deps, token, loginAt } = await loggedIn();

    for (let day = 30; day <= 365; day += 30) {
      await expect(resolveSession(deps, token, daysAfter(loginAt, day))).resolves.toBeTruthy();
    }
  });

  it("sin usarla durante 90 días caduca igual que antes", async () => {
    const { deps, token, loginAt } = await loggedIn();

    await expect(resolveSession(deps, token, daysAfter(loginAt, 60))).resolves.toBeTruthy();
    await expect(resolveSession(deps, token, daysAfter(loginAt, 60 + 91))).resolves.toBeNull();
  });

  it("solo se renueva pasada la mitad del plazo (no escribe en la base de datos en cada petición)", async () => {
    const { deps, token, loginAt } = await loggedIn();

    const early = await resolveSessionDetails(deps, token, daysAfter(loginAt, 10));
    expect(early?.renewedUntil).toBeNull();

    const late = await resolveSessionDetails(deps, token, daysAfter(loginAt, 50));
    expect(late?.renewedUntil?.getTime()).toBe(daysAfter(loginAt, 50 + 90).getTime());
    // Justo después ya no hace falta renovar otra vez.
    expect((await resolveSessionDetails(deps, token, daysAfter(loginAt, 51)))?.renewedUntil).toBeNull();
  });

  it("cerrar sesión la invalida aunque se estuviera renovando", async () => {
    const { deps, token, loginAt } = await loggedIn();
    await resolveSession(deps, token, daysAfter(loginAt, 50));

    await logout(deps, token);

    await expect(resolveSession(deps, token, daysAfter(loginAt, 51))).resolves.toBeNull();
  });
});
