import { cookies } from "next/headers";
import { SESSION_COOKIE_NAME } from "./auth/session";
import { resolveSession, type AuthServiceDeps } from "./services/auth-service";

/** Resolves the logged-in user id from the session cookie, or null. */
export async function getSessionUserId(deps: AuthServiceDeps): Promise<string | null> {
  const store = await cookies();
  return resolveSession(deps, store.get(SESSION_COOKIE_NAME)?.value);
}

/**
 * Resolves the logged-in user id from a Request, checking both the session
 * cookie (for web clients) and the Authorization header (for Android/API
 * clients). Cookie is checked first for backward compatibility.
 */
export async function getSessionUserIdFromRequest(
  deps: AuthServiceDeps,
  request: Request,
): Promise<string | null> {
  // 1. Check session cookie (web clients)
  const store = await cookies();
  const cookieToken = store.get(SESSION_COOKIE_NAME)?.value;
  if (cookieToken) {
    return resolveSession(deps, cookieToken);
  }

  // 2. Check Authorization: Bearer header (Android/API clients)
  const authHeader = request.headers.get("authorization");
  if (authHeader?.startsWith("Bearer ")) {
    const bearerToken = authHeader.slice(7);
    return resolveSession(deps, bearerToken);
  }

  return null;
}
