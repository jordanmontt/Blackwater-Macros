import { NextResponse } from "next/server";
import { eq } from "drizzle-orm";
import { getSessionUserId } from "@/server/api-auth";
import { serviceDeps } from "@/server/composition";
import { db } from "@/server/db/client";
import { users } from "@/server/db/schema";

export async function GET() {
  const userId = await getSessionUserId(serviceDeps.auth);
  if (!userId) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  const rows = await db
    .select({
      username: users.username,
      proteinGoal: users.proteinGoal,
      gender: users.gender,
      birthYear: users.birthYear,
      heightCm: users.heightCm,
      gymDaysPerWeek: users.gymDaysPerWeek,
      gymSessionMinutes: users.gymSessionMinutes,
      walkingMinutesPerDay: users.walkingMinutesPerDay,
      calorieGoal: users.calorieGoal,
    })
    .from(users)
    .where(eq(users.id, userId))
    .limit(1);
  const row = rows[0];
  return NextResponse.json({
    username: row?.username ?? "",
    proteinGoal: row?.proteinGoal ?? "build",
    calorieProfile: {
      gender: row?.gender ?? null,
      birthYear: row?.birthYear ?? null,
      heightCm: row?.heightCm ?? null,
      gymDaysPerWeek: row?.gymDaysPerWeek ?? null,
      gymSessionMinutes: row?.gymSessionMinutes ?? null,
      walkingMinutesPerDay: row?.walkingMinutesPerDay ?? null,
      calorieGoal: row?.calorieGoal ?? null,
    },
  });
}
