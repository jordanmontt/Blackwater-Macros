import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { SESSION_COOKIE_NAME } from "@/server/auth/session";
import { logout } from "@/server/services/auth-service";
import { serviceDeps } from "@/server/composition";

export async function POST(request: Request) {
  const store = await cookies();
  const cookieToken = store.get(SESSION_COOKIE_NAME)?.value;

  // Check cookie first, then Authorization header
  let token = cookieToken;
  if (!token) {
    const authHeader = request.headers.get("authorization");
    if (authHeader?.startsWith("Bearer ")) {
      token = authHeader.slice(7);
    }
  }

  await logout(serviceDeps.auth, token);
  const response = NextResponse.json({ ok: true });
  response.cookies.set(SESSION_COOKIE_NAME, "", {
    httpOnly: true,
    sameSite: "lax",
    secure: true,
    path: "/",
    maxAge: 0,
  });
  return response;
}
