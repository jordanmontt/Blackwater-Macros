import { describe, expect, it } from "vitest";
import { createMemoryWorld, seedUser } from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";
import { updateUser, deleteUser, AdminGuardError } from "@/server/services/admin-service";

/**
 * Pruebas de las salvaguardas del servicio de administración. El acceso a las
 * rutas exige un administrador autenticado (withAdmin), así que la invariante
 * «debe quedar al menos un administrador» se comprueba aquí directamente.
 */
function makeWorld(): MemoryWorld {
  return createMemoryWorld();
}

describe("servicio de administración", () => {
  it("impide que el último administrador sea degradado", async () => {
    const world = makeWorld();
    const target = await seedUser(world, "boss", "clave-secreta-1", true);
    await expect(
      updateUser(world.serviceDeps.auth, "otro-actor", target.id, { isAdmin: false }),
    ).rejects.toThrow(AdminGuardError);
  });

  it("impide que el último administrador sea borrado", async () => {
    const world = makeWorld();
    const target = await seedUser(world, "boss", "clave-secreta-1", true);
    await expect(
      deleteUser(world.serviceDeps.auth, "otro-actor", target.id),
    ).rejects.toThrow(AdminGuardError);
  });
});