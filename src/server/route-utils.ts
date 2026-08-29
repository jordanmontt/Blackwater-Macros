import { NextResponse } from "next/server";
import { ZodError } from "zod";
import { getSessionUserId } from "./api-auth";
import { serviceDeps } from "./composition";
import type { UserRow } from "./db/schema";
import { UsernameExistsError } from "./services/auth-service";
import { AdminGuardError, NotFoundError } from "./services/admin-service";

export function jsonError(message: string, status: number) {
  return NextResponse.json({ error: message }, { status });
}

/** Wraps a handler so it only runs for authenticated users. */
export async function withUserId(
  handler: (userId: string) => Promise<NextResponse | Response>,
): Promise<NextResponse | Response> {
  const userId = await getSessionUserId(serviceDeps.auth);
  if (!userId) return jsonError("No autenticado", 401);
  try {
    return await handler(userId);
  } catch (error) {
    if (error instanceof ZodError) {
      return jsonError(error.issues[0]?.message ?? "Datos no válidos", 400);
    }
    console.error("API error", error);
    return jsonError("Error interno", 500);
  }
}

/**
 * Wraps an admin handler so it only runs for authenticated administrators.
 * The check is a database lookup on every request: downgrading a user takes
 * effect immediately, even on devices with an already-open session.
 */
export async function withAdmin(
  handler: (actor: UserRow) => Promise<NextResponse | Response>,
): Promise<NextResponse | Response> {
  const userId = await getSessionUserId(serviceDeps.auth);
  if (!userId) return jsonError("No autenticado", 401);
  const actor = await serviceDeps.auth.users.findById(userId);
  if (!actor?.isAdmin) return jsonError("No autorizado", 403);
  try {
    return await handler(actor);
  } catch (error) {
    if (error instanceof ZodError) {
      return jsonError(error.issues[0]?.message ?? "Datos no válidos", 400);
    }
    if (error instanceof UsernameExistsError) {
      return jsonError(error.message, 409);
    }
    if (error instanceof NotFoundError) {
      return jsonError(error.message, 404);
    }
    if (error instanceof AdminGuardError) {
      return jsonError(error.message, 400);
    }
    console.error("API error", error);
    return jsonError("Error interno", 500);
  }
}

export async function parseJsonBody(request: Request): Promise<unknown> {
  return request.json();
}