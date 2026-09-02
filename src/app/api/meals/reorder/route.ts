import { NextResponse } from "next/server";
import { reorderMeals } from "@/server/services/meals-service";
import { repositories } from "@/server/composition";
import { parseJsonBody, withUserId } from "@/server/route-utils";
import { z } from "zod";

const reorderSchema = z.object({
  orderedIds: z.array(z.string().uuid()).min(1),
});

export async function PATCH(request: Request) {
  return withUserId(request, async (userId) => {
    const body = reorderSchema.parse(await parseJsonBody(request));
    await reorderMeals(repositories.meals, userId, body.orderedIds);
    return NextResponse.json({ ok: true });
  });
}
