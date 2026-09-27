import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HoyPage from "@/app/page";
import type { FoodProduct } from "@/lib/core/foods";
import type { MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { t } from "@/i18n";

/**
 * Requisitos de «Buscar» y «Código de barras» en «Añadir comida»:
 *  - buscar encuentra alimentos genéricos en español (también por sinónimos:
 *    «banana» → «Plátano») y productos de Open Food Facts,
 *  - elegir uno pide la cantidad y abre el formulario de revisión ya relleno,
 *    que se guarda como una comida normal por ingredientes,
 *  - el código de barras se puede escribir si no hay cámara; si no existe, lo dice,
 *  - desde el formulario, «Buscar alimento» añade otro alimento a la misma comida.
 */

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    listMeals: vi.fn(async () => [] as MealDTO[]),
    listTemplates: vi.fn(async () => [] as MealTemplateDTO[]),
    createMeal: vi.fn(async (payload: { title: string }) => ({ ...payload, id: "m-new" }) as unknown as MealDTO),
    reorderMeals: vi.fn(async () => ({ ok: true as const })),
    listWeights: vi.fn(async () => []),
    searchFoods: vi.fn(async () => [] as FoodProduct[]),
    session: vi.fn(async () => ({
      username: "demo",
      isAdmin: false,
      calorieProfile: {
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
      },
    })),
  } satisfies Pick<
    typeof import("@/lib/api").api,
    "listMeals" | "listTemplates" | "createMeal" | "reorderMeals" | "listWeights" | "searchFoods" | "session"
  >,
}));

import { api } from "@/lib/api";
import { clearCache } from "@/lib/client-cache";

const GENERIC_INDEX = {
  version: 1,
  foods: [
    { id: "ch:1", s: "ch", n: { es: "Plátano", en: "Banana, raw" }, v: [89, 1.1, 22.8, 0.3] },
    { id: "ch:2", s: "ch", n: { es: "Arroz blanco, cocido", en: "Rice, cooked" }, v: [130, 2.7, 28, 0.3] },
  ],
};

const OFF_PRODUCT = {
  status: 1,
  product: {
    code: "8480000592170",
    product_name: "Greek yogurt",
    product_name_es: "Yogur griego natural",
    brands: "Hacendado",
    serving_quantity: 125,
    nutriments: { "energy-kcal_100g": 122, proteins_100g: 3.5, carbohydrates_100g: 4.2, fat_100g: 10 },
  },
};

function stubFetch() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/foods/generic.json")) return new Response(JSON.stringify(GENERIC_INDEX));
      if (url.includes("/api/v2/product/8480000592170")) return new Response(JSON.stringify(OFF_PRODUCT));
      if (url.includes("/api/v2/product/")) return new Response(JSON.stringify({ status: 0 }));
      return new Response("not found", { status: 404 });
    }),
  );
}

async function openSheet(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByText(t.hoy.emptyDay);
  await user.click(screen.getAllByRole("button", { name: t.hoy.addMeal })[0]);
  return screen.findByRole("dialog", { name: t.addFood.title });
}

describe("Añadir comida: buscar y código de barras", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
    localStorage.clear();
    stubFetch();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("busca «banana», elige «Plátano», indica los gramos y guarda la comida revisada", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openSheet(user);

    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.search}`) }));
    await user.type(await screen.findByRole("searchbox", { name: t.addFood.search }), "banana");
    await user.click(await screen.findByRole("button", { name: /Plátano/ }));

    const grams = await screen.findByLabelText(t.addFood.gramsLabel);
    expect(grams).toHaveValue("100");
    await user.clear(grams);
    await user.type(grams, "120");
    expect(screen.getByTestId("portion-totals")).toHaveTextContent("107");
    await user.click(screen.getByRole("button", { name: t.addFood.addItem }));

    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    expect(within(form).getByLabelText(t.meal.titleLabel)).toHaveValue("Plátano");
    expect(within(form).getByDisplayValue("120 g")).toBeInTheDocument();
    await user.click(within(form).getByRole("button", { name: t.meal.save }));

    await waitFor(() =>
      expect(vi.mocked(api.createMeal)).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Plátano",
          entryMode: "per_ingredient",
          ingredients: [{ name: "Plátano", quantity: "120 g", calories: 107, protein: 1.3, carbs: 27.4, fat: 0.4 }],
        }),
      ),
    );
  });

  it("muestra productos de Open Food Facts y usa su ración como cantidad", async () => {
    vi.mocked(api.searchFoods).mockResolvedValue([
      {
        code: "8480000592170",
        name: "Yogur griego natural",
        brand: "Hacendado",
        per100g: { calories: 122, protein: 3.5, carbs: 4.2, fat: 10 },
        servingGrams: 125,
        incomplete: false,
      },
    ]);
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openSheet(user);

    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.search}`) }));
    await user.type(await screen.findByRole("searchbox", { name: t.addFood.search }), "yogur");
    await user.click(await screen.findByRole("button", { name: /Yogur griego natural/ }, { timeout: 3000 }));

    expect(vi.mocked(api.searchFoods)).toHaveBeenCalledWith("yogur", "es");
    expect(await screen.findByLabelText(t.addFood.gramsLabel)).toHaveValue("125");
  });

  it("sin cámara deja escribir el código de barras y encuentra el producto", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openSheet(user);

    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.barcode}`) }));
    expect(await screen.findByText(t.addFood.cameraError)).toBeInTheDocument();
    await user.type(screen.getByLabelText(t.addFood.manualCode), "8480000592170");
    await user.click(screen.getByRole("button", { name: t.addFood.lookUp }));

    expect(await screen.findByText("Yogur griego natural")).toBeInTheDocument();
    expect(screen.getByLabelText(t.addFood.gramsLabel)).toHaveValue("125");
  });

  it("dice cuándo un código no está en Open Food Facts", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    const sheet = await openSheet(user);

    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.barcode}`) }));
    await user.type(await screen.findByLabelText(t.addFood.manualCode), "1234567890123");
    await user.click(screen.getByRole("button", { name: t.addFood.lookUp }));

    expect(await screen.findByText(t.addFood.notFound)).toBeInTheDocument();
  });

  it("desde el formulario, «Buscar alimento» añade otro alimento a la misma comida", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    let sheet = await openSheet(user);

    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.search}`) }));
    await user.type(await screen.findByRole("searchbox", { name: t.addFood.search }), "platano");
    await user.click(await screen.findByRole("button", { name: /Plátano/ }));
    await user.click(await screen.findByRole("button", { name: t.addFood.addItem }));

    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    await user.click(within(form).getByRole("button", { name: t.addFood.searchFood }));

    sheet = await screen.findByRole("dialog", { name: t.addFood.title });
    // Opened from the form: only the food sources, not «Escribir a mano» or «Copiar».
    expect(within(sheet).queryByRole("button", { name: new RegExp(t.addFood.manual) })).toBeNull();
    await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.search}`) }));
    await user.type(await screen.findByRole("searchbox", { name: t.addFood.search }), "arroz");
    await user.click(await screen.findByRole("button", { name: /Arroz blanco, cocido/ }));
    await user.click(await screen.findByRole("button", { name: t.addFood.addItem }));

    await waitFor(() => expect(screen.queryByRole("dialog", { name: t.addFood.title })).toBeNull());
    const updated = screen.getByRole("dialog", { name: t.meal.newTitle });
    // Title and first ingredient are both «Plátano».
    expect(within(updated).getAllByDisplayValue("Plátano")).toHaveLength(2);
    expect(within(updated).getByDisplayValue("Arroz blanco, cocido")).toBeInTheDocument();
    expect(within(updated).getByLabelText(t.meal.titleLabel)).toHaveValue("Plátano");
  });
});
