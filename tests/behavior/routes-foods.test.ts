import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { createMemoryWorld, authenticateWith } from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";

/**
 * Ruta de búsqueda de productos (pasarela hacia Open Food Facts):
 *  - solo para usuarios con sesión,
 *  - valida la búsqueda,
 *  - devuelve los productos ya interpretados, con el nombre en el idioma pedido,
 *  - si Open Food Facts falla, responde 502 sin romper nada.
 */

const holder = vi.hoisted(() => ({
  world: null as MemoryWorld | null,
  authCookie: { value: "" },
}));

vi.mock("@/server/composition", () => ({
  get repositories() {
    return holder.world?.repositories;
  },
  get serviceDeps() {
    return holder.world?.serviceDeps;
  },
}));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "bw_session" && holder.authCookie.value ? { name, value: holder.authCookie.value } : undefined,
  }),
}));

import { GET as searchFoods } from "@/app/api/foods/search/route";

holder.world = createMemoryWorld();
const world = holder.world;

const HITS = {
  hits: [
    {
      code: "1",
      product_name: "Greek yogurt",
      product_name_es: "Yogur griego",
      brands: ["Hacendado"],
      nutriments: { "energy-kcal_100g": 122, proteins_100g: 3.5, carbohydrates_100g: 4.2, fat_100g: 10 },
    },
    { code: "2", product_name: "Sin energía", nutriments: {} },
  ],
};

describe("ruta de búsqueda de productos", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sin sesión responde 401 y no llama a Open Food Facts", async () => {
    const response = await searchFoods(new Request("http://test/api/foods/search?q=yogur"));
    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rechaza búsquedas vacías o demasiado largas", async () => {
    authenticateWith(holder.authCookie, world!, "u-1");
    expect((await searchFoods(new Request("http://test/api/foods/search?q=a"))).status).toBe(400);
    expect((await searchFoods(new Request(`http://test/api/foods/search?q=${"x".repeat(81)}`))).status).toBe(400);
  });

  it("devuelve los productos interpretados, en español y con User-Agent propio", async () => {
    authenticateWith(holder.authCookie, world!, "u-1");
    fetchMock.mockResolvedValue(new Response(JSON.stringify(HITS)));

    const response = await searchFoods(new Request("http://test/api/foods/search?q=yogur%20griego&lang=es"));
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      products: [
        {
          code: "1",
          name: "Yogur griego",
          brand: "Hacendado",
          per100g: { calories: 122, protein: 3.5, carbs: 4.2, fat: 10 },
          servingGrams: null,
          incomplete: false,
        },
      ],
    });
    const [url, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
    expect(url.searchParams.get("q")).toBe("yogur griego");
    expect(url.searchParams.get("langs")).toBe("es,en");
    expect((init.headers as Record<string, string>)["User-Agent"]).toMatch(/^BlackwaterMacros\//);
  });

  it("si Open Food Facts falla responde 502", async () => {
    authenticateWith(holder.authCookie, world!, "u-1");
    fetchMock.mockResolvedValue(new Response("busy", { status: 503 }));
    expect((await searchFoods(new Request("http://test/api/foods/search?q=yogur"))).status).toBe(502);
    fetchMock.mockRejectedValue(new Error("offline"));
    expect((await searchFoods(new Request("http://test/api/foods/search?q=yogur"))).status).toBe(502);
  });
});
