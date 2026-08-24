import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { SESSION_COOKIE_NAME } from "@/server/auth/session";
import { logout } from "@/server/services/auth-service";
import { serviceDeps } from "@/server/composition";

export async function POST() {
  const store = await cookies();
  await logout(serviceDeps.auth, store.get(SESSION_COOKIE_NAME)?.value);
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
