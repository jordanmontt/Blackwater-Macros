import { cookies } from "next/headers";
import { SESSION_COOKIE_NAME, sessionCookieOptions } from "./auth/session";
import { resolveSessionDetails, type AuthServiceDeps } from "./services/auth-service";

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
    const session = await resolveSessionDetails(deps, cookieToken);
    // The session got a new 90 days: so does the browser's cookie (it carries the old end).
    if (session?.renewedUntil) {
      try {
        store.set(SESSION_COOKIE_NAME, cookieToken, { ...sessionCookieOptions, expires: session.renewedUntil });
      } catch {
        // Only a route handler can set cookies (all callers are). Elsewhere the session is
        // still renewed; only the browser's cookie keeps its old end date.
      }
    }
    return session?.userId ?? null;
  }

  // 2. Check Authorization: Bearer header (Android/API clients)
  const authHeader = request.headers.get("authorization");
  if (authHeader?.startsWith("Bearer ")) {
    const bearerToken = authHeader.slice(7);
    return (await resolveSessionDetails(deps, bearerToken))?.userId ?? null;
  }

  return null;
}
