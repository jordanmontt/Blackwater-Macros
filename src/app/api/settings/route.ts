import { NextResponse } from "next/server";
import { withUserId, parseJsonBody } from "@/server/route-utils";
import { repositories } from "@/server/composition";
import { calorieProfileInputSchema } from "@/server/validation";
import { updateCalorieProfile } from "@/server/services/settings-service";

export async function PUT(request: Request) {
  return withUserId(async (userId) => {
    const body = (await parseJsonBody(request)) as Record<string, unknown>;

    if (
      "gender" in body ||
      "birthYear" in body ||
      "heightCm" in body ||
      "calorieGoal" in body
    ) {
      const input = calorieProfileInputSchema.parse(body);
      const settings = await updateCalorieProfile(repositories.settings, userId, input);
      return NextResponse.json(settings);
    }

    return NextResponse.json({ error: "Payload no válido" }, { status: 400 });
  });
}
