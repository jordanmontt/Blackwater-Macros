import type { Context } from "hono";
import { ZodError } from "zod";
import { SESSION_COOKIE_NAME } from "@/server/auth/session";
import type { AuthServiceDeps } from "@/server/services/auth-service";
import { resolveSession } from "@/server/services/auth-service";

export function jsonError(message: string, status: number): Response {
  return Response.json({ error: message }, { status });
}

/**
 * Extracts the session token from a request: the `Authorization: Bearer`
 * header first (used by Flutter web and Android), then the `bw_session`
 * cookie (used by the legacy browser web app).
 */
export function sessionTokenFromRequest(c: Context): string | undefined {
  const authorization = c.req.header("authorization");
  if (authorization?.startsWith("Bearer ")) {
    const token = authorization.slice("Bearer ".length).trim();
    if (token) return token;
  }
  const cookie = c.req.header("cookie");
  if (cookie) {
    for (const part of cookie.split(";")) {
      const eq = part.indexOf("=");
      if (eq === -1) continue;
      const name = part.slice(0, eq).trim();
      const value = part.slice(eq + 1).trim();
      if (name === SESSION_COOKIE_NAME && value) return value;
    }
  }
  return undefined;
}

/**
 * Wraps an authenticated handler. Resolves the userId from the presented
 * token (Bearer header or cookie) and rejects with 401 when missing/expired.
 * ZodError → 400 with the first issue message; anything else → logged + 500.
 */
export function withUserId(
  deps: AuthServiceDeps,
  handler: (c: Context, userId: string) => Promise<Response>,
): (c: Context) => Promise<Response> {
  return async (c) => {
    const userId = await resolveSession(deps, sessionTokenFromRequest(c));
    if (!userId) return jsonError("No autenticado", 401);
    try {
      return await handler(c, userId);
    } catch (error) {
      if (error instanceof ZodError) {
        return jsonError(error.issues[0]?.message ?? "Datos no válidos", 400);
      }
      console.error("API error", error);
      return jsonError("Error interno", 500);
    }
  };
}

function secureSuffix(): string {
  return process.env.NODE_ENV === "production" ? "; Secure" : "";
}

/** `Set-Cookie` value that establishes a session (mirrors the legacy web app). */
export function sessionCookie(token: string, expiresAt: Date): string {
  return `${SESSION_COOKIE_NAME}=${token}; Path=/; HttpOnly; SameSite=Lax${secureSuffix()}; Expires=${expiresAt.toUTCString()}`;
}

/** `Set-Cookie` value that clears the session cookie on logout. */
export function clearSessionCookie(): string {
  return `${SESSION_COOKIE_NAME}=; Path=/; HttpOnly; SameSite=Lax${secureSuffix()}; Max-Age=0`;
}