import { hashPassword } from "../auth/password";
import type { UserRow } from "../db/schema";
import type { AdminUserDTO } from "@/lib/core/types";
import { UsernameExistsError, type AuthServiceDeps } from "./auth-service";

export class NotFoundError extends Error {
  constructor() {
    super("Usuario no encontrado");
    this.name = "NotFoundError";
  }
}

export class AdminGuardError extends Error {
  constructor(public readonly code: "cannot_demote_self" | "cannot_delete_self" | "last_admin") {
    super(code);
    this.name = "AdminGuardError";
  }
}

/** Número de administradores actuales (para proteger la cuenta del último). */
async function countAdmins(deps: AuthServiceDeps): Promise<number> {
  const users = await deps.users.list();
  return users.filter((user) => user.isAdmin).length;
}

function toAdminUserDTO(user: UserRow): AdminUserDTO {
  return {
    id: user.id,
    username: user.username,
    isAdmin: user.isAdmin,
    createdAt: user.createdAt.toISOString(),
  };
}

/** Lista todos los usuarios sin exponer credenciales ni perfiles privados. */
export async function listUsers(deps: AuthServiceDeps): Promise<AdminUserDTO[]> {
  const users = await deps.users.list();
  return users.map(toAdminUserDTO);
}

/**
 * Actualiza un usuario (nombre, contraseña o rol) con salvaguardas:
 * - el nombre no puede colisionar con otro usuario (409),
 * - el administrador que actúa no puede quitarte el rol a sí mismo (400),
 * - el último administrador no puede ser degradado (400).
 */
export async function updateUser(
  deps: AuthServiceDeps,
  actorId: string,
  targetId: string,
  changes: { username?: string; password?: string; isAdmin?: boolean },
): Promise<AdminUserDTO> {
  const target = await deps.users.findById(targetId);
  if (!target) throw new NotFoundError();

  const data: { username?: string; passwordHash?: string; isAdmin?: boolean } = {};

  if (changes.username !== undefined && changes.username !== target.username) {
    const normalized = changes.username.trim().toLowerCase();
    const existing = await deps.users.findByUsername(normalized);
    if (existing && existing.id !== targetId) throw new UsernameExistsError();
    data.username = normalized;
  }

  if (changes.password !== undefined) {
    data.passwordHash = await hashPassword(changes.password);
  }

  if (changes.isAdmin !== undefined && changes.isAdmin !== target.isAdmin) {
    if (changes.isAdmin === false) {
      if (actorId === targetId) {
        throw new AdminGuardError("cannot_demote_self");
      }
      if (target.isAdmin && (await countAdmins(deps)) <= 1) {
        throw new AdminGuardError("last_admin");
      }
    }
    data.isAdmin = changes.isAdmin;
  }

  const updated = await deps.users.update(targetId, data);
  if (!updated) throw new NotFoundError();
  return toAdminUserDTO(updated);
}

/**
 * Borra un usuario y su rol de los sistemas. Salvaguardas:
 * - no puedes borrarte a ti mismo (400),
 * - el último administrador no puede ser borrado (400).
 */
export async function deleteUser(
  deps: AuthServiceDeps,
  actorId: string,
  targetId: string,
): Promise<void> {
  const target = await deps.users.findById(targetId);
  if (!target) throw new NotFoundError();
  if (actorId === targetId) {
    throw new AdminGuardError("cannot_delete_self");
  }
  if (target.isAdmin && (await countAdmins(deps)) <= 1) {
    throw new AdminGuardError("last_admin");
  }
  await deps.users.delete(targetId);
}