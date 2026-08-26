import { NextResponse } from "next/server";
import { weightInputSchema } from "@/server/validation";
import { createWeight, listWeights } from "@/server/services/weights-service";
import { repositories } from "@/server/composition";
import { parseJsonBody, withUserId } from "@/server/route-utils";

export async function GET() {
  return withUserId(async (userId) => {
    const weights = await listWeights(repositories.weights, userId);
    return NextResponse.json({ weights });
  });
}

export async function POST(request: Request) {
  return withUserId(async (userId) => {
    const input = weightInputSchema.parse(await parseJsonBody(request));
    const weight = await createWeight(repositories.weights, userId, input);
    return NextResponse.json({ weight }, { status: 201 });
  });
}
