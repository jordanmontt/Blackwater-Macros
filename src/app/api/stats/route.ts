import { NextResponse } from "next/server";
import { buildStatsSummary } from "@/server/services/stats-service";
import { serviceDeps } from "@/server/composition";
import { withUserId } from "@/server/route-utils";
import { isValidDateKey, todayKey } from "@/lib/core/dates";
import type { StatsRange } from "@/lib/core/types";

const RANGE_VALUES: StatsRange[] = ["7d", "30d", "90d", "all"];

export async function GET(request: Request) {
  return withUserId(request, async (userId) => {
    const url = new URL(request.url);
    const rangeParam = url.searchParams.get("range") ?? "30d";
    const range = (RANGE_VALUES as string[]).includes(rangeParam)
      ? (rangeParam as StatsRange)
      : "30d";
    const todayParam = url.searchParams.get("today");
    const today = todayParam && isValidDateKey(todayParam) ? todayParam : todayKey();

    const summary = await buildStatsSummary(serviceDeps.stats, userId, range, today);
    return NextResponse.json(summary);
  });
}
