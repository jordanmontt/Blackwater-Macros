import { NextResponse } from "next/server";
import { recordIdSchema, templateInputSchema } from "@/server/validation";
import { deleteTemplate, updateTemplate, upsertTemplate } from "@/server/services/templates-service";
import { repositories } from "@/server/composition";
import { jsonError, parseJsonBody, withUserId } from "@/server/route-utils";

type RouteContext = { params: Promise<{ id: string }> };

export async function PATCH(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    const input = templateInputSchema.parse(await parseJsonBody(request));
    const template = await updateTemplate(repositories.templates, userId, id, input);
    if (!template) return jsonError("Plantilla no encontrada", 404);
    return NextResponse.json({ template });
  });
}

/** Idempotent create-or-replace with a client-generated id (Android offline sync). */
export async function PUT(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    recordIdSchema.parse(id);
    const input = templateInputSchema.parse(await parseJsonBody(request));
    const template = await upsertTemplate(repositories.templates, userId, id, input);
    if (!template) return jsonError("Plantilla no encontrada", 404);
    return NextResponse.json({ template });
  });
}

export async function DELETE(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    const deleted = await deleteTemplate(repositories.templates, userId, id);
    if (!deleted) return jsonError("Plantilla no encontrada", 404);
    return NextResponse.json({ ok: true });
  });
}
