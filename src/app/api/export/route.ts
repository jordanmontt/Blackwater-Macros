import { backupFileName } from "@/lib/core/backup";
import { isValidDateKey } from "@/lib/core/dates";
import { repositories } from "@/server/composition";
import { withUserId } from "@/server/route-utils";
import { buildUserBackup } from "@/server/services/export-service";

/**
 * The export file. `?day=` is the browser's date (YYYY-MM-DD), for the file name;
 * without it, the server's UTC date.
 */
export async function GET(request: Request) {
  return withUserId(request, async (userId) => {
    const now = new Date();
    const asked = new URL(request.url).searchParams.get("day");
    const day = asked && isValidDateKey(asked) ? asked : now.toISOString().slice(0, 10);
    return new Response(await buildUserBackup(repositories, userId, now), {
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Content-Disposition": `attachment; filename="${backupFileName(day)}"`,
        "Cache-Control": "no-store",
      },
    });
  });
}
