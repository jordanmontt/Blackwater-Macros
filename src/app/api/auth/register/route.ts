import { NextResponse } from "next/server";
import { register, UsernameExistsError } from "@/server/services/auth-service";
import { serviceDeps } from "@/server/composition";
import { registerInputSchema } from "@/server/validation";

/**
 * Creación de cuentas vía API. De momento el registro está deshabilitado: la
 * creación de usuarios la hará una página de administración que aún no existe.
 * La lógica ya está implementada (validación + comprobación de duplicados en el
 * servicio) y cubierta por tests a nivel de servicio; cuando llegue la página
 * de administración se quitará la guarda.
 */
const REGISTRATION_ENABLED = false;

export async function POST(request: Request) {
  if (!REGISTRATION_ENABLED) {
    return NextResponse.json({ error: "Registro deshabilitado" }, { status: 403 });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Cuerpo JSON no válido" }, { status: 400 });
  }

  const parsed = registerInputSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: "Datos no válidos" }, { status: 400 });
  }

  try {
    await register(serviceDeps.auth, parsed.data.username, parsed.data.password);
    return NextResponse.json({ ok: true }, { status: 201 });
  } catch (error) {
    if (error instanceof UsernameExistsError) {
      return NextResponse.json({ error: error.message }, { status: 409 });
    }
    console.error("register failed", error);
    return NextResponse.json({ error: "Error interno" }, { status: 500 });
  }
}