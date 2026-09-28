import { jsonError } from "@/server/route-utils";
import { NextResponse } from "next/server";
import { z } from "zod";
import { mealInputSchema, weightInputSchema } from "@/server/validation";
import { importMeals, importWeights } from "@/server/services/import-service";
import { repositories } from "@/server/composition";
import { parseJsonBody, withUserId } from "@/server/route-utils";

/** A CSV backup parsed in the browser (`lib/csv-import.ts`); duplicates are skipped. */
const mealsBody = z.object({ meals: z.array(mealInputSchema).max(20_000) });
const weightsBody = z.object({ weights: z.array(weightInputSchema).max(20_000) });

export async function POST(request: Request, context: { params: Promise<{ kind: string }> }) {
  const { kind } = await context.params;
  return withUserId(request, async (userId) => {
    const body = await parseJsonBody(request);
    if (kind === "meals") {
      const { meals } = mealsBody.parse(body);
      return NextResponse.json(await importMeals(repositories.meals, userId, meals));
    }
    if (kind === "weights") {
      const { weights } = weightsBody.parse(body);
      return NextResponse.json(await importWeights(repositories.weights, userId, weights));
    }
    return jsonError("not_found", 404);
  });
}
