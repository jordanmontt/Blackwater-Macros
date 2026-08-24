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
    .select({ username: users.username })
    .from(users)
    .where(eq(users.id, userId))
    .limit(1);
  return NextResponse.json({ username: rows[0]?.username ?? "" });
}
