import { NextResponse } from "next/server";
import { ZodError } from "zod";
import { getSessionUserIdFromRequest } from "./api-auth";
import { serviceDeps } from "./composition";
import type { UserRow } from "./db/schema";
import { UsernameExistsError } from "./services/auth-service";
import { AdminGuardError, NotFoundError } from "./services/admin-service";
import { isServerErrorCode, serverErrorText, type ServerErrorCode } from "@/lib/server-errors";

/** `{ error, code }`: clients translate the code (see `lib/server-errors.ts`). */
export function jsonError(code: ServerErrorCode, status: number) {
  return NextResponse.json({ error: serverErrorText(code), code }, { status });
}

/** Schemas carry codes as their messages; anything else (zod's own messages) is `invalid_data`. */
export function zodErrorCode(error: ZodError): ServerErrorCode {
  const message = error.issues[0]?.message;
  return isServerErrorCode(message) ? message : "invalid_data";
}

function isJsonSyntaxError(error: unknown): boolean {
  return error instanceof SyntaxError;
}

/** Wraps a handler so it only runs for authenticated users. */
export async function withUserId(
  request: Request,
  handler: (userId: string) => Promise<NextResponse | Response>,
): Promise<NextResponse | Response> {
  const userId = await getSessionUserIdFromRequest(serviceDeps.auth, request);
  if (!userId) return jsonError("unauthenticated", 401);
  try {
    return await handler(userId);
  } catch (error) {
    if (error instanceof ZodError) {
      return jsonError(zodErrorCode(error), 400);
    }
    if (isJsonSyntaxError(error)) return jsonError("invalid_json", 400);
    console.error("API error", error);
    return jsonError("internal", 500);
  }
}

/**
 * Wraps an admin handler so it only runs for authenticated administrators.
 * The check is a database lookup on every request: downgrading a user takes
 * effect immediately, even on devices with an already-open session.
 */
export async function withAdmin(
  request: Request,
  handler: (actor: UserRow) => Promise<NextResponse | Response>,
): Promise<NextResponse | Response> {
  const userId = await getSessionUserIdFromRequest(serviceDeps.auth, request);
  if (!userId) return jsonError("unauthenticated", 401);
  const actor = await serviceDeps.auth.users.findById(userId);
  if (!actor?.isAdmin) return jsonError("forbidden", 403);
  try {
    return await handler(actor);
  } catch (error) {
    if (error instanceof ZodError) {
      return jsonError(zodErrorCode(error), 400);
    }
    if (isJsonSyntaxError(error)) return jsonError("invalid_json", 400);
    if (error instanceof UsernameExistsError) {
      return jsonError("username_exists", 409);
    }
    if (error instanceof NotFoundError) {
      return jsonError("user_not_found", 404);
    }
    if (error instanceof AdminGuardError) {
      return jsonError(error.code, 400);
    }
    console.error("API error", error);
    return jsonError("internal", 500);
  }
}

export async function parseJsonBody(request: Request): Promise<unknown> {
  return request.json();
}