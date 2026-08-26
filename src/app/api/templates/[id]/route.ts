import { NextResponse } from "next/server";
import { deleteTemplate } from "@/server/services/templates-service";
import { repositories } from "@/server/composition";
import { jsonError, withUserId } from "@/server/route-utils";

type RouteContext = { params: Promise<{ id: string }> };

export async function DELETE(_request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(async (userId) => {
    const deleted = await deleteTemplate(repositories.templates, userId, id);
    if (!deleted) return jsonError("Plantilla no encontrada", 404);
    return NextResponse.json({ ok: true });
  });
}
