import { NextResponse } from "next/server";
import { withAdmin, parseJsonBody, jsonError } from "@/server/route-utils";
import { serviceDeps } from "@/server/composition";
import { deleteUser, updateUser } from "@/server/services/admin-service";
import { adminUpdateUserSchema, type AdminUpdateUserInput } from "@/server/validation";

export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  return withAdmin(async (actor) => {
    let body: unknown;
    try {
      body = await parseJsonBody(request);
    } catch {
      return jsonError("Cuerpo JSON no válido", 400);
    }

    const parsed = adminUpdateUserSchema.safeParse(body);
    if (!parsed.success) {
      return jsonError(parsed.error.issues[0]?.message ?? "Datos no válidos", 400);
    }

    const changes: AdminUpdateUserInput = parsed.data;
    const user = await updateUser(serviceDeps.auth, actor.id, id, {
      username: changes.username,
      password: changes.password,
      isAdmin: changes.isAdmin,
    });
    return NextResponse.json({ user });
  });
}

export async function DELETE(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return withAdmin(async (actor) => {
    await deleteUser(serviceDeps.auth, actor.id, id);
    return NextResponse.json({ ok: true });
  });
}