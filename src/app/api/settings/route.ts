import { NextResponse } from "next/server";
import { withUserId, parseJsonBody } from "@/server/route-utils";
import { repositories } from "@/server/composition";
import { settingsInputSchema } from "@/server/validation";
import { updateSettings } from "@/server/services/settings-service";

export async function PUT(request: Request) {
  return withUserId(async (userId) => {
    const input = settingsInputSchema.parse(await parseJsonBody(request));
    const settings = await updateSettings(repositories.settings, userId, input);
    return NextResponse.json(settings);
  });
}
