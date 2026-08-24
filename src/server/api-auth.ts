import { cookies } from "next/headers";
import { SESSION_COOKIE_NAME } from "./auth/session";
import { resolveSession, type AuthServiceDeps } from "./services/auth-service";

/** Resolves the logged-in user id from the session cookie, or null. */
export async function getSessionUserId(deps: AuthServiceDeps): Promise<string | null> {
  const store = await cookies();
  return resolveSession(deps, store.get(SESSION_COOKIE_NAME)?.value);
}
