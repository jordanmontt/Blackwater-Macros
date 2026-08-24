import { NextResponse } from "next/server";
import { mealInputSchema } from "@/server/validation";
import { createMeal, listMealsInRange } from "@/server/services/meals-service";
import { serviceDeps } from "@/server/composition";
import { parseJsonBody, withUserId } from "@/server/route-utils";

export async function GET(request: Request) {
  return withUserId(async (userId) => {
    const url = new URL(request.url);
    const from = url.searchParams.get("from");
    const to = url.searchParams.get("to");
    const meals = await listMealsInRange(serviceDeps.meals, userId, from, to);
    return NextResponse.json({ meals });
  });
}

export async function POST(request: Request) {
  return withUserId(async (userId) => {
    const input = mealInputSchema.parse(await parseJsonBody(request));
    const meal = await createMeal(serviceDeps.meals, userId, input);
    return NextResponse.json({ meal }, { status: 201 });
  });
}
