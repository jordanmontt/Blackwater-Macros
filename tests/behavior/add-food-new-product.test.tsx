import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HoyPage from "@/app/page";
import OffAccountPage from "@/app/ajustes/open-food-facts/page";
import { OwnProductUploads } from "@/components/own-product-uploads";
import type { FoodProduct } from "@/lib/core/foods";
import type { MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos de «Añadir este producto» (un código que Open Food Facts no tiene):
 *  - el aviso de «no encontrado» ofrece añadirlo con su tabla nutricional, salvo una
 *    etiqueta de la tienda (su código cambia con cada paquete),
 *  - el formulario pide nombre, kcal y proteína; acepta la tabla por 100 g, 100 ml o
 *    por ración (p. ej. 200 ml, como en Bolivia) y dice cuánto es por 100,
 *  - al guardar se pasa a la cantidad, como un producto encontrado, con «Tu producto»,
 *  - se envía directamente a Open Food Facts (nunca a nuestro servidor) con la cuenta
 *    de la app o la del usuario, y la foto de la tabla después; sin conexión espera y
 *    se envía al volver la conexión,
 *  - el siguiente escaneo y la búsqueda lo encuentran en este navegador,
 *  - «Editar» corrige los valores y vuelve a enviarlos,
 *  - con IA, la foto de la tabla rellena los valores,
 *  - al editar se ve la foto de la tabla que espera en este navegador, y al corregir la
 *    que tiene Open Food Facts,
 *  - un producto que Open Food Facts tiene con valores que no coinciden con la tabla
 *    se corrige desde la cantidad («Corregir valores»): el formulario parte de sus
 *    valores, muestra lo que tenía junto a cada valor cambiado, no guarda sin cambios
 *    y envía la corrección en la base que Open Food Facts guarda (sin nombre ni marca).
 * `fetch` está simulado: nunca se llama a Open Food Facts de verdad.
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
  },
}));

// The app account's password is filled in at release time (RELEASING.md); the tests use one.
vi.mock("@/lib/core/new-product", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/core/new-product")>()),
  OFF_APP_PASSWORD: "app-password",
}));

// happy-dom has no canvas/createImageBitmap: the downscale itself is a browser API.
vi.mock("@/lib/ai/images", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/ai/images")>()),
  downscalePhoto: vi.fn(async () => ({ mimeType: "image/jpeg", data: "TEFCRUw=" })),
}));

import { clearCache } from "@/lib/client-cache";

const UNKNOWN = "7771234000123";
const KNOWN_WITHOUT_NUTRITION = "8410000000017";
const SHOP_LABEL = "2098053024201";
/** Open Food Facts has it per 100 ml with an old recipe's values (and no fat). */
const WRONG_VALUES = "7771234000222";

const fetchMock = vi.fn<typeof fetch>();
let offline = false;

function stubFetch() {
  fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (offline) throw new TypeError("Failed to fetch");
    if (url.endsWith("/foods/generic.json")) return new Response(JSON.stringify({ version: 1, foods: [] }));
    if (url.includes(`/api/v2/product/${KNOWN_WITHOUT_NUTRITION}`)) {
      return new Response(
        JSON.stringify({ status: 1, product: { product_name: "Galletas María", brands: "Fontaneda", nutriments: {} } }),
      );
    }
    if (url.includes(`/api/v2/product/${WRONG_VALUES}`)) {
      const product = {
        code: WRONG_VALUES,
        product_name: "Leche entera",
        brands: "PIL",
        serving_size: "200 ml",
        nutrition_data_per: "100g",
        nutriments: { "energy-kcal_100g": 64, proteins_100g: 3.2, carbohydrates_100g: 4.8 },
        image_nutrition_url: `https://images.openfoodfacts.org/images/products/777/123/400/0222/nutrition_es.3.400.jpg`,
      };
      return new Response(JSON.stringify({ status: 1, product }));
    }
    if (url.includes("/api/v2/product/")) return new Response(JSON.stringify({ status: 0 }));
    if (url.endsWith("/cgi/product_jqm2.pl")) return new Response(JSON.stringify({ status: 1, status_verbose: "fields saved" }));
    if (url.endsWith("/cgi/product_image_upload.pl")) return new Response(JSON.stringify({ status: "status ok" }));
    if (url.endsWith("/cgi/auth.pl")) {
      const body = init?.body as URLSearchParams;
      return new Response("{}", { status: body.get("password") === "right" ? 200 : 403 });
    }
    if (url.includes("generativelanguage.googleapis.com")) {
      const reading = { name: "Leche entera", brand: "PIL", basis: "serving", servingSize: "200 ml", calories: 124, protein: 6.4, carbs: 9.6, fat: 6.6 };
      return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: JSON.stringify(reading) }] } }] }));
    }
    return new Response("not found", { status: 404 });
  });
}

function writes(path: string) {
  return fetchMock.mock.calls.filter(([url]) => String(url).endsWith(path));
}

function sentFields(call = 0): URLSearchParams {
  return writes("/cgi/product_jqm2.pl")[call][1]!.body as URLSearchParams;
}

async function lookUp(user: ReturnType<typeof userEvent.setup>, code: string) {
  await screen.findByText(t.hoy.emptyDay);
  await user.click(screen.getAllByRole("button", { name: t.hoy.addMeal })[0]);
  const sheet = await screen.findByRole("dialog", { name: t.addFood.title });
  await user.click(within(sheet).getByRole("button", { name: new RegExp(`^${t.addFood.barcode}`) }));
  await user.type(await screen.findByLabelText(t.addFood.manualCode), code);
  await user.click(screen.getByRole("button", { name: t.addFood.lookUp }));
}

async function fillMilk(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(t.newProduct.name), "Leche entera");
  await user.type(screen.getByLabelText(new RegExp(`^${t.newProduct.brand}`)), "PIL");
  await user.click(screen.getByRole("button", { name: t.newProduct.basisServing }));
  await user.type(screen.getByLabelText(t.newProduct.servingSize), "200 ml");
  await user.type(screen.getByLabelText(t.newProduct.calories), "124");
  await user.type(screen.getByLabelText(t.newProduct.protein), "6,4");
}

describe("Añadir comida: añadir un producto que Open Food Facts no tiene", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
    localStorage.clear();
    offline = false;
    fetchMock.mockReset();
    stubFetch();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("se añade con la tabla por ración, se registra y se envía a Open Food Facts", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, UNKNOWN);

    expect(await screen.findByText(t.addFood.notFoundAdd)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.addFood.addProduct }));
    expect(await screen.findByRole("dialog", { name: t.newProduct.title })).toBeInTheDocument();
    expect(screen.getByText(formatTemplate(t.newProduct.barcode, { code: UNKNOWN }))).toBeInTheDocument();
    expect(screen.getByText(t.newProduct.consent)).toBeInTheDocument();

    await fillMilk(user);
    expect(screen.getByTestId("per-100-line")).toHaveTextContent(
      formatTemplate(t.newProduct.per100, { kcal: "62", p: "3,2", unit: "ml" }),
    );
    await user.click(screen.getByRole("button", { name: t.newProduct.save }));

    // The amount screen, like a found product: one serving (200 ml) to start with.
    expect(await screen.findByLabelText(t.addFood.gramsLabel)).toHaveValue("200");
    expect(screen.getByText(t.newProduct.yourProduct)).toBeInTheDocument();
    expect(await screen.findByText(t.newProduct.status.sent)).toBeInTheDocument();

    const fields = sentFields();
    expect(fields.get("code")).toBe(UNKNOWN);
    expect(fields.get("user_id")).toBe("blackwater-macros-app");
    expect(fields.get("password")).toBe("app-password");
    expect(fields.get("product_name_es")).toBe("Leche entera");
    expect(fields.get("add_brands")).toBe("PIL");
    expect(fields.get("nutrition_data_per")).toBe("serving");
    expect(fields.get("serving_size")).toBe("200 ml");
    expect(fields.get("nutriment_energy-kcal")).toBe("124");
    expect(fields.get("nutriment_proteins")).toBe("6.4");
    expect(fields.get("app_uuid")).toMatch(/^[0-9a-f-]{36}$/);
    // Only Open Food Facts is written to: nothing goes to our server.
    for (const [url] of fetchMock.mock.calls.filter(([, init]) => init?.method === "POST")) {
      expect(String(url)).toMatch(/^https:\/\/world\.openfoodfacts\.org\/cgi\//);
    }

    await user.click(screen.getByRole("button", { name: t.addFood.addItem }));
    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    expect(within(form).getAllByDisplayValue("Leche entera").length).toBeGreaterThan(0);
  });

  it("sin nombre, kcal o proteína no se guarda y dice qué falta", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, UNKNOWN);
    await user.click(await screen.findByRole("button", { name: t.addFood.addProduct }));
    await user.click(await screen.findByRole("button", { name: t.newProduct.save }));

    expect(screen.getByText(t.newProduct.errors.name)).toBeInTheDocument();
    expect(screen.getByText(t.newProduct.errors.calories)).toBeInTheDocument();
    expect(screen.getByText(t.newProduct.errors.protein)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.newProduct.basisServing }));
    expect(screen.getByText(t.newProduct.errors.serving)).toBeInTheDocument();
    expect(writes("/cgi/product_jqm2.pl")).toHaveLength(0);
    expect(localStorage.getItem("bw:own-products")).toBeNull();
  });

  it("una etiqueta de la tienda no ofrece añadirlo", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, SHOP_LABEL);
    expect(await screen.findByText(t.addFood.notFoundStore)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: t.addFood.addProduct })).toBeNull();
  });

  it("a un producto que Open Food Facts conoce sin nutrición solo le añade los valores", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, KNOWN_WITHOUT_NUTRITION);
    expect(await screen.findByText(t.addFood.notFoundNoNutrition)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.addFood.addProduct }));

    expect(await screen.findByLabelText(t.newProduct.name)).toHaveValue("Galletas María");
    expect(screen.getByLabelText(new RegExp(`^${t.newProduct.brand}`))).toHaveValue("Fontaneda");
    expect(screen.getByText(t.newProduct.consentExisting)).toBeInTheDocument();
    await user.type(screen.getByLabelText(t.newProduct.calories), "440");
    await user.type(screen.getByLabelText(t.newProduct.protein), "7");
    await user.click(screen.getByRole("button", { name: t.newProduct.save }));

    await screen.findByText(t.newProduct.status.sent);
    expect(sentFields().get("product_name_es")).toBeNull();
    expect(sentFields().get("add_brands")).toBeNull();
    expect(sentFields().get("nutrition_data_per")).toBe("100g");
  });

  it("sin conexión queda pendiente y se envía al volver la conexión", async () => {
    const user = userEvent.setup();
    render(
      <>
        <OwnProductUploads />
        <HoyPage />
      </>,
    );
    await lookUp(user, UNKNOWN);
    await user.click(await screen.findByRole("button", { name: t.addFood.addProduct }));
    await fillMilk(user);
    offline = true;
    await user.click(screen.getByRole("button", { name: t.newProduct.save }));

    expect(await screen.findByText(t.newProduct.yourProduct)).toBeInTheDocument();
    expect(screen.queryByText(t.newProduct.status.sent)).toBeNull();
    expect(JSON.parse(localStorage.getItem("bw:own-products")!)[0].state).toBe("pending");

    offline = false;
    window.dispatchEvent(new Event("online"));
    expect(await screen.findByText(t.newProduct.status.sent)).toBeInTheDocument();
  });

  it("el siguiente escaneo y la búsqueda lo encuentran; «Editar» corrige y reenvía", async () => {
    const user = userEvent.setup();
    const { unmount } = render(<HoyPage />);
    await lookUp(user, UNKNOWN);
    await user.click(await screen.findByRole("button", { name: t.addFood.addProduct }));
    await fillMilk(user);
    await user.click(screen.getByRole("button", { name: t.newProduct.save }));
    await screen.findByText(t.newProduct.status.sent);
    unmount();

    render(<HoyPage />);
    await lookUp(user, UNKNOWN);
    expect(await screen.findByText(t.newProduct.yourProduct)).toBeInTheDocument();
    expect(screen.getByText(/Leche entera/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: t.newProduct.edit }));
    const kcal = await screen.findByLabelText(t.newProduct.calories);
    await user.clear(kcal);
    await user.type(kcal, "130");
    await user.click(screen.getByRole("button", { name: t.newProduct.saveEdit }));

    await waitFor(() => expect(writes("/cgi/product_jqm2.pl")).toHaveLength(2));
    expect(sentFields(1).get("nutriment_energy-kcal")).toBe("130");
    expect(
      await screen.findByText(formatTemplate(t.addFood.per100, { kcal: "65", p: "3,2" })),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: t.addFood.back }));
    await user.click(screen.getByRole("button", { name: t.addFood.back }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: new RegExp(`^${t.addFood.search}`) }));
    await user.type(await screen.findByRole("searchbox", { name: t.addFood.search }), "leche");
    const own = await screen.findByRole("region", { name: t.addFood.yourProducts });
    expect(within(own).getByText(/Leche entera/)).toBeInTheDocument();
  });

  it("al editar se ve la foto de la tabla que aún espera en este navegador", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, UNKNOWN);
    await user.click(await screen.findByRole("button", { name: t.addFood.addProduct }));
    await user.upload(screen.getByTestId("label-photo-input"), new File(["x"], "label.jpg", { type: "image/jpeg" }));
    await fillMilk(user);
    offline = true;
    await user.click(screen.getByRole("button", { name: t.newProduct.save }));

    await user.click(await screen.findByRole("button", { name: t.newProduct.edit }));
    expect(await screen.findByTestId("saved-label-photo")).toHaveAttribute("src", "data:image/jpeg;base64,TEFCRUw=");
    expect(screen.getByText(t.newProduct.photoWaiting)).toBeInTheDocument();
    expect(screen.getByLabelText(t.newProduct.calories)).toHaveValue("124");
  });

  it("con IA, la foto de la tabla rellena los valores y se envía después de ellos", async () => {
    localStorage.setItem("bw:ai", JSON.stringify({ provider: "gemini", apiKeys: { gemini: "fake-key" } }));
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, UNKNOWN);
    await user.click(await screen.findByRole("button", { name: t.addFood.addProduct }));
    expect(await screen.findByText(t.newProduct.labelPhotoHintAi)).toBeInTheDocument();

    await user.upload(screen.getByTestId("label-photo-input"), new File(["x"], "label.jpg", { type: "image/jpeg" }));
    expect(await screen.findByText(t.newProduct.readDone)).toBeInTheDocument();
    expect(screen.getByLabelText(t.newProduct.name)).toHaveValue("Leche entera");
    expect(screen.getByLabelText(t.newProduct.servingSize)).toHaveValue("200 ml");
    expect(screen.getByLabelText(t.newProduct.calories)).toHaveValue("124");
    expect(screen.getByLabelText(t.newProduct.protein)).toHaveValue("6,4");
    expect(screen.getByRole("button", { name: t.newProduct.basisServing })).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: t.newProduct.save }));
    await screen.findByText(t.newProduct.status.sent);
    const order = fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.includes("/cgi/"));
    expect(order).toEqual([
      "https://world.openfoodfacts.org/cgi/product_jqm2.pl",
      "https://world.openfoodfacts.org/cgi/product_image_upload.pl",
    ]);
    const photo = writes("/cgi/product_image_upload.pl")[0][1]!.body as FormData;
    expect(photo.get("imagefield")).toBe("nutrition_es");
    expect(photo.get("imgupload_nutrition_es")).toBeInstanceOf(Blob);
    // Once Open Food Facts has it, the photo leaves this browser.
    expect(JSON.parse(localStorage.getItem("bw:own-products")!)[0].photo).toBeNull();
  });
});

describe("Añadir comida: corregir los valores de un producto de Open Food Facts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
    localStorage.clear();
    offline = false;
    fetchMock.mockReset();
    stubFetch();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("parte de sus valores, muestra lo que cambia y envía la corrección por 100 ml", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, WRONG_VALUES);
    await screen.findByLabelText(t.addFood.gramsLabel);
    await user.click(screen.getByRole("button", { name: t.newProduct.correctLink }));

    expect(await screen.findByRole("dialog", { name: t.newProduct.correctTitle })).toBeInTheDocument();
    expect(screen.getByText(t.newProduct.correctCheck)).toBeInTheDocument();
    expect(screen.getByText(t.newProduct.consentCorrection)).toBeInTheDocument();
    // Its label photo, to compare with the package in hand.
    expect(screen.getByTestId("saved-label-photo")).toHaveAttribute("src", expect.stringContaining("nutrition_es.3.400.jpg"));
    expect(screen.getByText(t.newProduct.photoOnOff)).toBeInTheDocument();
    // The name and brand stay Open Food Facts'; the values start from what it has.
    expect(screen.queryByLabelText(t.newProduct.name)).toBeNull();
    expect(screen.getByLabelText(t.newProduct.calories)).toHaveValue("64");
    expect(screen.getByLabelText(t.newProduct.fat)).toHaveValue("");

    await user.click(screen.getByRole("button", { name: t.newProduct.save }));
    expect(screen.getByText(t.newProduct.errors.unchanged)).toBeInTheDocument();
    expect(writes("/cgi/product_jqm2.pl")).toHaveLength(0);

    // The label in hand is per 200 ml serving: 124 kcal instead of 128, and the fat.
    await user.click(screen.getByRole("button", { name: t.newProduct.basisServing }));
    expect(screen.getByLabelText(t.newProduct.servingSize)).toHaveValue("200 ml");
    for (const [label, value] of [
      [t.newProduct.calories, "124"],
      [t.newProduct.protein, "6,4"],
      [t.newProduct.carbs, "9,6"],
      [t.newProduct.fat, "6,6"],
    ]) {
      await user.clear(screen.getByLabelText(label));
      await user.type(screen.getByLabelText(label), value);
    }
    expect(screen.getByText(formatTemplate(t.newProduct.offValue, { value: "128" }))).toBeInTheDocument();
    expect(screen.queryByText(formatTemplate(t.newProduct.offValue, { value: "6,4" }))).toBeNull();
    await user.click(screen.getByRole("button", { name: t.newProduct.save }));

    expect(await screen.findByText(t.newProduct.yourCorrection)).toBeInTheDocument();
    expect(await screen.findByText(t.newProduct.status.sentCorrection)).toBeInTheDocument();
    expect(screen.getByText(formatTemplate(t.addFood.per100, { kcal: "62", p: "3,2" }))).toBeInTheDocument();
    const fields = sentFields();
    expect(fields.get("nutrition_data_per")).toBe("100g");
    expect(fields.get("nutriment_energy-kcal")).toBe("62");
    expect(fields.get("nutriment_fat")).toBe("3.3");
    expect(fields.get("product_name_es")).toBeNull();
    expect(fields.get("add_brands")).toBeNull();
  });

  it("sin conexión dice que no se pudo y no abre el formulario", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await lookUp(user, WRONG_VALUES);
    await screen.findByLabelText(t.addFood.gramsLabel);
    offline = true;
    await user.click(screen.getByRole("button", { name: t.newProduct.correctLink }));
    expect(await screen.findByText(t.newProduct.correctFailed)).toBeInTheDocument();
    expect(screen.getByLabelText(t.addFood.gramsLabel)).toBeInTheDocument();
  });
});

describe("Ajustes → Open Food Facts", () => {
  beforeEach(() => {
    localStorage.clear();
    offline = false;
    fetchMock.mockReset();
    stubFetch();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("comprueba la cuenta del usuario y la usa para lo que añade", async () => {
    const user = userEvent.setup();
    render(<OffAccountPage />);
    expect(screen.getByText(t.offAccount.description)).toBeInTheDocument();
    expect(screen.getByText(t.offAccount.keptHere)).toBeInTheDocument();

    await user.type(screen.getByLabelText(t.offAccount.username), "maria");
    await user.type(screen.getByLabelText(t.offAccount.password), "wrong");
    await user.click(screen.getByRole("button", { name: t.offAccount.save }));
    expect(await screen.findByText(t.offAccount.wrong)).toBeInTheDocument();
    expect(localStorage.getItem("bw:off-account")).toBeNull();

    await user.clear(screen.getByLabelText(t.offAccount.password));
    await user.type(screen.getByLabelText(t.offAccount.password), "right");
    await user.click(screen.getByRole("button", { name: t.offAccount.save }));
    expect(await screen.findByText(formatTemplate(t.offAccount.signedIn, { user: "maria" }))).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem("bw:off-account")!)).toEqual({ userId: "maria", password: "right" });

    await user.click(screen.getByRole("button", { name: t.offAccount.signOut }));
    expect(localStorage.getItem("bw:off-account")).toBe("null");
    expect(screen.getByLabelText(t.offAccount.username)).toBeInTheDocument();
  });
});
