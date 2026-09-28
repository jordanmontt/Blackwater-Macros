import { NextResponse } from "next/server";
import { withAdmin, parseJsonBody, jsonError, zodErrorCode } from "@/server/route-utils";
import { serviceDeps } from "@/server/composition";
import { listUsers } from "@/server/services/admin-service";
import { register } from "@/server/services/auth-service";
import { registerInputSchema } from "@/server/validation";

export async function GET(request: Request) {
  return withAdmin(request, async () => {
    const users = await listUsers(serviceDeps.auth);
    return NextResponse.json({ users });
  });
}

export async function POST(request: Request) {
  return withAdmin(request, async () => {
    let body: unknown;
    try {
      body = await parseJsonBody(request);
    } catch {
      return jsonError("invalid_json", 400);
    }

    const parsed = registerInputSchema.safeParse(body);
    if (!parsed.success) {
      return jsonError(zodErrorCode(parsed.error), 400);
    }

    await register(serviceDeps.auth, parsed.data.username, parsed.data.password);
    return NextResponse.json({ ok: true }, { status: 201 });
  });
}