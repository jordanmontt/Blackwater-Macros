import { NextResponse } from "next/server";
import { templateInputSchema } from "@/server/validation";
import { createTemplate, listTemplates } from "@/server/services/templates-service";
import { repositories } from "@/server/composition";
import { parseJsonBody, withUserId } from "@/server/route-utils";

export async function GET(request: Request) {
  return withUserId(request, async (userId) => {
    const templates = await listTemplates(repositories.templates, userId);
    return NextResponse.json({ templates });
  });
}

export async function POST(request: Request) {
  return withUserId(request, async (userId) => {
    const input = templateInputSchema.parse(await parseJsonBody(request));
    const template = await createTemplate(repositories.templates, userId, input);
    return NextResponse.json({ template }, { status: 201 });
  });
}
