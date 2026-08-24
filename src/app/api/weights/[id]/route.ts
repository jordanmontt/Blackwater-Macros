import { NextResponse } from "next/server";
import { weightInputSchema } from "@/server/validation";
import { deleteWeight, updateWeight } from "@/server/services/weights-service";
import { serviceDeps } from "@/server/composition";
import { jsonError, parseJsonBody, withUserId } from "@/server/route-utils";

type RouteContext = { params: Promise<{ id: string }> };

export async function PATCH(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(async (userId) => {
    const input = weightInputSchema.parse(await parseJsonBody(request));
    const weight = await updateWeight(serviceDeps.weights, userId, id, input);
    if (!weight) return jsonError("Registro no encontrado", 404);
    return NextResponse.json({ weight });
  });
}

export async function DELETE(_request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(async (userId) => {
    const deleted = await deleteWeight(serviceDeps.weights, userId, id);
    if (!deleted) return jsonError("Registro no encontrado", 404);
    return NextResponse.json({ ok: true });
  });
}
