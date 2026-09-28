import { NextResponse } from "next/server";
import { recordIdSchema, weightInputSchema } from "@/server/validation";
import { deleteWeight, updateWeight, upsertWeight } from "@/server/services/weights-service";
import { repositories } from "@/server/composition";
import { jsonError, parseJsonBody, withUserId } from "@/server/route-utils";

type RouteContext = { params: Promise<{ id: string }> };

export async function PATCH(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    const input = weightInputSchema.parse(await parseJsonBody(request));
    const weight = await updateWeight(repositories.weights, userId, id, input);
    if (!weight) return jsonError("weight_not_found", 404);
    return NextResponse.json({ weight });
  });
}

/** Idempotent create-or-replace with a client-generated id (Android offline sync). */
export async function PUT(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    recordIdSchema.parse(id);
    const input = weightInputSchema.parse(await parseJsonBody(request));
    const weight = await upsertWeight(repositories.weights, userId, id, input);
    if (!weight) return jsonError("weight_not_found", 404);
    return NextResponse.json({ weight });
  });
}

export async function DELETE(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    const deleted = await deleteWeight(repositories.weights, userId, id);
    if (!deleted) return jsonError("weight_not_found", 404);
    return NextResponse.json({ ok: true });
  });
}
