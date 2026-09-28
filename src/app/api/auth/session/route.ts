import { jsonError } from "@/server/route-utils";
import { NextResponse } from "next/server";
import { getSessionUserIdFromRequest } from "@/server/api-auth";
import { serviceDeps } from "@/server/composition";

export async function GET(request: Request) {
  const userId = await getSessionUserIdFromRequest(serviceDeps.auth, request);
  if (!userId) {
    return jsonError("unauthenticated", 401);
  }
  const user = await serviceDeps.auth.users.findById(userId);
  return NextResponse.json({
    username: user?.username ?? "",
    isAdmin: user?.isAdmin ?? false,
    calorieProfile: {
      gender: user?.gender ?? null,
      birthYear: user?.birthYear ?? null,
      heightCm: user?.heightCm ?? null,
      gymDaysPerWeek: user?.gymDaysPerWeek ?? null,
      gymSessionMinutes: user?.gymSessionMinutes ?? null,
      walkingMinutesPerDay: user?.walkingMinutesPerDay ?? null,
      calorieGoal: user?.calorieGoal ?? null,
    },
  });
}