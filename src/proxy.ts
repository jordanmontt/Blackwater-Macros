import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const SESSION_COOKIE_NAME = "bw_session";
const DEMO_COOKIE_NAME = "bw_demo";
const PUBLIC_PATHS = ["/login"];

/**
 * Optimistic gate: pages other than /login require a session cookie (or the
 * client-side demo cookie) to be present. The session cookie's validity is
 * verified server-side by every API route, and the demo cookie grants no API
 * access — it only lets the edge render protected pages so the purely
 * client-side demo mode can run in the browser.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasSessionCookie = Boolean(request.cookies.get(SESSION_COOKIE_NAME)?.value);
  const hasDemoCookie = Boolean(request.cookies.get(DEMO_COOKIE_NAME)?.value);

  if (PUBLIC_PATHS.some((path) => pathname.startsWith(path))) {
    // Only a real session bounces the login page back to the app; a demo
    // cookie keeps /login reachable so the demo user can exit to log in.
    if (hasSessionCookie) {
      return NextResponse.redirect(new URL("/", request.url));
    }
    return NextResponse.next();
  }

  if (!hasSessionCookie && !hasDemoCookie) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|icon|manifest|.*\\.png$).*)"],
};
