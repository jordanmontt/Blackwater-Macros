import { NextResponse } from "next/server";
import { withAdmin, parseJsonBody, jsonError, zodErrorCode } from "@/server/route-utils";
import { serviceDeps } from "@/server/composition";
import { deleteUser, updateUser } from "@/server/services/admin-service";
import { adminUpdateUserSchema, type AdminUpdateUserInput } from "@/server/validation";

export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  return withAdmin(request, async (actor) => {
    let body: unknown;
    try {
      body = await parseJsonBody(request);
    } catch {
      return jsonError("invalid_json", 400);
    }

    const parsed = adminUpdateUserSchema.safeParse(body);
    if (!parsed.success) {
      return jsonError(zodErrorCode(parsed.error), 400);
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

export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return withAdmin(request, async (actor) => {
    await deleteUser(serviceDeps.auth, actor.id, id);
    return NextResponse.json({ ok: true });
  });
}