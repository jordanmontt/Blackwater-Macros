import { NextResponse } from "next/server";
import { ZodError } from "zod";
import { getSessionUserId } from "./api-auth";
import { serviceDeps } from "./composition";

export function jsonError(message: string, status: number) {
  return NextResponse.json({ error: message }, { status });
}

/** Wraps a handler so it only runs for authenticated users. */
export async function withUserId(
  handler: (userId: string) => Promise<NextResponse | Response>,
): Promise<NextResponse | Response> {
  const userId = await getSessionUserId(serviceDeps.auth);
  if (!userId) return jsonError("No autenticado", 401);
  try {
    return await handler(userId);
  } catch (error) {
    if (error instanceof ZodError) {
      return jsonError(error.issues[0]?.message ?? "Datos no válidos", 400);
    }
    console.error("API error", error);
    return jsonError("Error interno", 500);
  }
}

export async function parseJsonBody(request: Request): Promise<unknown> {
  return request.json();
}
