import { NextResponse } from "next/server";
import { withUserId, jsonError } from "@/server/route-utils";
import { parseOffSearch, type FoodLang } from "@/lib/core/foods";

/** Open Food Facts full-text search (Search-a-licious). */
const OFF_SEARCH_URL = "https://search.openfoodfacts.org/search";
const USER_AGENT = "BlackwaterMacros/1.0 (https://github.com/jordanmontt/Blackwater-Macros)";
const LANGS: FoodLang[] = ["es", "en", "fr", "de", "it"];
const FIELDS = "code,product_name,product_name_es,product_name_en,product_name_fr,product_name_de,product_name_it,generic_name,brands,serving_size,serving_quantity,nutriments";

/**
 * Pass-through to Open Food Facts text search, only for signed-in users.
 * Needed because Search-a-licious does not allow browser (CORS) requests.
 * Stores nothing: forwards the query and returns the parsed products.
 */
export async function GET(request: Request) {
  return withUserId(request, async () => {
    const url = new URL(request.url);
    const query = (url.searchParams.get("q") ?? "").trim();
    if (query.length < 2 || query.length > 80) return jsonError("Búsqueda no válida", 400);
    const langParam = url.searchParams.get("lang") as FoodLang | null;
    const lang = langParam && LANGS.includes(langParam) ? langParam : "es";

    const upstream = new URL(OFF_SEARCH_URL);
    upstream.searchParams.set("q", query);
    upstream.searchParams.set("langs", lang === "en" ? "en" : `${lang},en`);
    upstream.searchParams.set("page_size", "20");
    upstream.searchParams.set("fields", FIELDS);

    let response: Response;
    try {
      response = await fetch(upstream, {
        headers: { "User-Agent": USER_AGENT, Accept: "application/json" },
        signal: AbortSignal.timeout(8000),
      });
    } catch {
      return jsonError("Open Food Facts no responde", 502);
    }
    if (!response.ok) return jsonError("Open Food Facts no responde", 502);
    const products = parseOffSearch(await response.json(), lang);
    return NextResponse.json({ products });
  });
}
