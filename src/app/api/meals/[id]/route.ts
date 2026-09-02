import { NextResponse } from "next/server";
import { mealInputSchema } from "@/server/validation";
import { deleteMeal, updateMeal } from "@/server/services/meals-service";
import { repositories } from "@/server/composition";
import { jsonError, parseJsonBody, withUserId } from "@/server/route-utils";

type RouteContext = { params: Promise<{ id: string }> };

export async function PATCH(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    const input = mealInputSchema.parse(await parseJsonBody(request));
    const meal = await updateMeal(repositories.meals, userId, id, input);
    if (!meal) return jsonError("Comida no encontrada", 404);
    return NextResponse.json({ meal });
  });
}

export async function DELETE(request: Request, context: RouteContext) {
  const { id } = await context.params;
  return withUserId(request, async (userId) => {
    const deleted = await deleteMeal(repositories.meals, userId, id);
    if (!deleted) return jsonError("Comida no encontrada", 404);
    return NextResponse.json({ ok: true });
  });
}
