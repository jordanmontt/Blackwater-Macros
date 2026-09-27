import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { NutritionRecommendationsCard } from "@/components/nutrition-recommendations";
import type { CalorieProfile, WeightDTO } from "@/lib/core/types";

/**
 * Requisitos visibles en las tarjetas de recomendación (calorías y proteína)
 * de la pantalla "Hoy":
 *  - la cantidad que falta para alcanzar el objetivo se muestra como un rango
 *    ("te faltan X–Y"), no como un número exacto,
 *  - el número grande es el promedio estimado del rango, no un objetivo exacto,
 *  - si la ingesta está dentro del rango se indica "en rango",
 *  - si se excede se muestra un rango ("te pasaste de X–Y").
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
    listWeights: vi.fn(),
    session: vi.fn(),
  } satisfies Pick<typeof import("@/lib/api").api, "listWeights" | "session">,
}));

import { api } from "@/lib/api";
import { clearCache } from "@/lib/client-cache";

const weight: WeightDTO = {
  id: "w1",
  measuredAt: "2026-01-01T00:00:00.000Z",
  weightKg: 80,
  bodyFatPct: null,
  note: null,
  updatedAt: "2026-01-01T00:00:00.000Z",
};

const profile: CalorieProfile = {
  gender: "male",
  birthYear: 1990,
  heightCm: 180,
  gymDaysPerWeek: 3,
  gymSessionMinutes: 60,
  walkingMinutesPerDay: 30,
  calorieGoal: "maintain",
};

describe("tarjetas de recomendación", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    vi.mocked(api.listWeights).mockResolvedValue([weight]);
    vi.mocked(api.session).mockResolvedValue({
      username: "demo",
      isAdmin: false,
      calorieProfile: profile,
    });
  });

  it("muestra la cantidad que falta como un rango, no un número exacto", async () => {
    // Ingesta 2000 kcal, objetivo rango (p. ej. 2200–2400) → "te faltan 200–400 kcal"
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    const missing = await screen.findByText(/te faltan \d+–\d+ kcal/);
    expect(missing.textContent).toMatch(/te faltan \d+–\d+ kcal/);
    expect(missing.textContent).not.toMatch(/te faltan \d+ kcal/);
  });

  it("acompaña el número grande con la etiqueta de promedio estimado", async () => {
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    // la etiqueta aparece dentro de un párrafo con el rango → matcher de substring
    const labels = await screen.findAllByText((content) => content.includes("Promedio estimado"));
    expect(labels.length).toBeGreaterThanOrEqual(2);
  });

  it("indica 'en rango' cuando la ingesta está dentro del objetivo", async () => {
    render(<NutritionRecommendationsCard dailyCalories={2700} dailyProtein={110} />);

    expect(await screen.findAllByText("en rango")).not.toHaveLength(0);
  });

  it("indica cuánto se ha pasado como un único valor (diferencia con el máximo)", async () => {
    render(<NutritionRecommendationsCard dailyCalories={3000} dailyProtein={140} />);

    const exceeded = await screen.findByText(/te pasaste de \d+ kcal/);
    expect(exceeded.textContent).toMatch(/te pasaste de \d+ kcal/);
    expect(exceeded.textContent).not.toMatch(/te pasaste de \d+–\d+ kcal/);
  });

  it("explica de dónde sale el objetivo: metabolismo basal y gasto diario (TDEE)", async () => {
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    expect(await screen.findByText((content) => content.startsWith("Metabolismo basal (TMB):"))).toBeInTheDocument();
    expect(screen.getByText((content) => content.startsWith("Gasto calórico diario estimado (TDEE):"))).toBeInTheDocument();
  });

  it("muestra promedio, TMB y TDEE antes de la barra de progreso", async () => {
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);
    const tdee = await screen.findByText((content) => content.startsWith("Gasto calórico diario estimado (TDEE):"));
    const missing = screen.getByText(/te faltan \d+–\d+ kcal/);
    // TDEE aparece antes que el estado de la barra en el orden del documento.
    expect(tdee.compareDocumentPosition(missing) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("la barra muestra lo consumido frente al objetivo y cambia de color al pasarse", async () => {
    const { rerender } = render(<NutritionRecommendationsCard dailyCalories={1200} dailyProtein={50} />);
    const bars = await screen.findAllByRole("progressbar");
    expect(bars[0]).toHaveAttribute("aria-valuenow", "1200");
    expect(screen.getAllByTestId("intake-fill")[0].className).toContain("bg-primary");
    // "consumido / mínimo–máximo kcal" en palabras, además de la barra
    expect(screen.getByText((_, el) => el?.tagName === "SPAN" && /^1\.?200 \/ [\d.]+–[\d.]+ kcal$/.test(el.textContent ?? ""))).toBeInTheDocument();

    rerender(<NutritionRecommendationsCard dailyCalories={4000} dailyProtein={50} />);
    expect(screen.getAllByTestId("intake-fill")[0].className).toContain("bg-tertiary");
  });
});
