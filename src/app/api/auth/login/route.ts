import { jsonError } from "@/server/route-utils";
import { NextResponse } from "next/server";
import { SESSION_COOKIE_NAME, sessionCookieOptions } from "@/server/auth/session";
import { login, InvalidCredentialsError } from "@/server/services/auth-service";
import { serviceDeps } from "@/server/composition";
import { loginInputSchema } from "@/server/validation";

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return jsonError("invalid_json", 400);
  }

  const parsed = loginInputSchema.safeParse(body);
  if (!parsed.success) {
    return jsonError("invalid_data", 400);
  }

  try {
    const issued = await login(serviceDeps.auth, parsed.data.username, parsed.data.password);
    const response = NextResponse.json({ ok: true, token: issued.token, expiresAt: issued.expiresAt.toISOString() });
    response.cookies.set(SESSION_COOKIE_NAME, issued.token, {
      ...sessionCookieOptions,
      expires: issued.expiresAt,
    });
    return response;
  } catch (error) {
    if (error instanceof InvalidCredentialsError) {
      return jsonError("invalid_credentials", 401);
    }
    console.error("login failed", error);
    return jsonError("internal", 500);
  }
}
