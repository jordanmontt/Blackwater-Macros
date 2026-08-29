import { describe, expect, it, beforeEach } from "vitest";
import { createMemoryWorld, seedUser } from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";
import { register, login, UsernameExistsError } from "@/server/services/auth-service";

/**
 * Requisitos del registro de cuentas (servicio):
 *  - crea el usuario con el nombre normalizado (minúsculas y sin espacios),
 *  - guarda la contraseña cifrada y nunca emite una sesión al registrar,
 *  - un nombre de usuario ya existente se rechaza en el servidor (no solo por
 *    la restricción única de la base de datos), sin distinguir mayúsculas.
 */

describe("registro de usuarios (servicio)", () => {
  let world: MemoryWorld;

  beforeEach(() => {
    world = createMemoryWorld();
  });

  it("crea el usuario en minúsculas, cifra la contraseña y no abre sesión", async () => {
    const user = await register(world.serviceDeps.auth, "  Ana  ", "clave-secreta-1");
    expect(user.username).toBe("ana");
    expect(user.passwordHash).toBeDefined();
    expect(user.passwordHash).not.toBe("clave-secreta-1");
    expect(user.passwordHash.startsWith("scrypt$")).toBe(true);
    expect(world.data.sessions.size).toBe(0);

    const issued = await login(world.serviceDeps.auth, "ana", "clave-secreta-1");
    expect(issued.token.length).toBeGreaterThan(0);
  });

  it("rechaza un usuario que ya existe", async () => {
    await seedUser(world, "ana", "otra-clave-1");
    await expect(
      register(world.serviceDeps.auth, "ana", "clave-secreta-1"),
    ).rejects.toThrow(UsernameExistsError);
    expect(world.data.users.size).toBe(1);
  });

  it("trata los nombres de usuario sin distinguir mayúsculas", async () => {
    await seedUser(world, "ana", "otra-clave-1");
    await expect(
      register(world.serviceDeps.auth, "ANA", "clave-secreta-1"),
    ).rejects.toThrow(UsernameExistsError);
  });
});