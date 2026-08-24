import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const SESSION_COOKIE_NAME = "bw_session";
const PUBLIC_PATHS = ["/login"];

/**
 * Optimistic gate: pages other than /login require a session cookie to be
 * present. The cookie's validity is verified server-side by every API route,
 * so a stale cookie simply results in a redirect back to /login.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasSessionCookie = Boolean(request.cookies.get(SESSION_COOKIE_NAME)?.value);

  if (PUBLIC_PATHS.some((path) => pathname.startsWith(path))) {
    if (hasSessionCookie) {
      return NextResponse.redirect(new URL("/", request.url));
    }
    return NextResponse.next();
  }

  if (!hasSessionCookie) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|icon|manifest|.*\\.png$).*)"],
};
