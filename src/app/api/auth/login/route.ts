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
    return NextResponse.json({ error: "Cuerpo JSON no válido" }, { status: 400 });
  }

  const parsed = loginInputSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: "Datos no válidos" }, { status: 400 });
  }

  try {
    const issued = await login(serviceDeps.auth, parsed.data.username, parsed.data.password);
    const response = NextResponse.json({ ok: true });
    response.cookies.set(SESSION_COOKIE_NAME, issued.token, {
      ...sessionCookieOptions,
      expires: issued.expiresAt,
    });
    return response;
  } catch (error) {
    if (error instanceof InvalidCredentialsError) {
      return NextResponse.json({ error: error.message }, { status: 401 });
    }
    console.error("login failed", error);
    return NextResponse.json({ error: "Error interno" }, { status: 500 });
  }
}
