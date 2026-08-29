import { NextResponse } from "next/server";
import { withAdmin, parseJsonBody, jsonError } from "@/server/route-utils";
import { serviceDeps } from "@/server/composition";
import { listUsers } from "@/server/services/admin-service";
import { register } from "@/server/services/auth-service";
import { registerInputSchema } from "@/server/validation";

export async function GET() {
  return withAdmin(async () => {
    const users = await listUsers(serviceDeps.auth);
    return NextResponse.json({ users });
  });
}

export async function POST(request: Request) {
  return withAdmin(async () => {
    let body: unknown;
    try {
      body = await parseJsonBody(request);
    } catch {
      return jsonError("Cuerpo JSON no válido", 400);
    }

    const parsed = registerInputSchema.safeParse(body);
    if (!parsed.success) {
      return jsonError(parsed.error.issues[0]?.message ?? "Datos no válidos", 400);
    }

    await register(serviceDeps.auth, parsed.data.username, parsed.data.password);
    return NextResponse.json({ ok: true }, { status: 201 });
  });
}