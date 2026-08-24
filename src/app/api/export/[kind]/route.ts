import {
  buildMealsCsv,
  buildWeightsCsv,
} from "@/server/services/export-service";
import { serviceDeps } from "@/server/composition";
import { withUserId } from "@/server/route-utils";

function csvResponse(filename: string, body: string) {
  return new Response(body, {
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": `attachment; filename="${filename}"`,
    },
  });
}

export async function GET(request: Request, context: { params: Promise<{ kind: string }> }) {
  const { kind } = await context.params;
  return withUserId(async (userId) => {
    if (kind === "meals") {
      const meals = await serviceDeps.stats.meals.listInRange(userId, null, null);
      return csvResponse("comidas.csv", buildMealsCsv(meals));
    }
    if (kind === "weights") {
      const weights = await serviceDeps.stats.weights.listForUser(userId);
      return csvResponse("peso.csv", buildWeightsCsv(weights));
    }
    return Response.json({ error: "Recurso no encontrado" }, { status: 404 });
  });
}
