import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { formatTemplate, t } from "@/i18n";
import { NutritionRecommendationsCard } from "@/components/nutrition-recommendations";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import type { CalorieProfile, MealDTO, WeightDTO } from "@/lib/core/types";
import { calculateCalorieRecommendation } from "@/lib/core/calories";
import { formatNumber } from "@/i18n/format";

/**
 * Requisitos visibles en las tarjetas de recomendación (calorías y proteína)
 * de la pantalla "Hoy":
 *  - la cantidad que falta para alcanzar el objetivo se muestra como un rango
 *    ("te faltan X–Y"), no como un número exacto,
 *  - el número grande es el promedio estimado del rango, no un objetivo exacto,
 *  - si la ingesta está dentro del rango se indica "en rango",
 *  - si se excede se muestra un rango ("te pasaste de X–Y"),
 *  - con 4 semanas de comidas y pesajes frecuentes aparece el gasto medido
 *    con su margen; con pesajes semanales no aparece,
 *  - en definición con % de grasa, la proteína se calcula sobre la masa magra,
 *  - si el gasto medido queda claramente fuera del de tu perfil (dos semanas
 *    seguidas), una línea lo dice; «¿Por qué?» enseña el cálculo con tus
 *    números y «Ahora no» la oculta; si solo faltan pesajes, lo dice una vez,
 *  - un día pasado conserva los objetivos que tenía: cambiar el perfil cuenta
 *    desde hoy (como MyFitnessPal), y la tarjeta dice que son los de ese día.
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
    listMeals: vi.fn(),
    session: vi.fn(),
  } satisfies Pick<typeof import("@/lib/api").api, "listWeights" | "listMeals" | "session">,
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
    localStorage.clear();
    vi.mocked(api.listWeights).mockResolvedValue([weight]);
    vi.mocked(api.listMeals).mockResolvedValue([]);
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

  /** One weigh-in every `every` days over the last 4 weeks, stable at 80 kg. */
  function weighIns(every: number): WeightDTO[] {
    const rows: WeightDTO[] = [];
    for (let n = 28; n >= 1; n -= every) {
      const day = addDaysToKey(todayKey(), -n);
      rows.push({ ...weight, id: `w${n}`, measuredAt: `${day}T08:00:00`, weightKg: 80 });
    }
    return rows;
  }

  function mealsEveryDay(kcal: number): MealDTO[] {
    return Array.from({ length: 28 }, (_, i) => ({
      id: `m${i}`,
      logDate: addDaysToKey(todayKey(), -(i + 1)),
      title: "Comida",
      notes: null,
      entryMode: "total_only",
      ingredients: [],
      totalCalories: kcal,
      totalProtein: 100,
      totalCarbs: null,
      totalFat: null,
      resolvedCalories: kcal,
      resolvedProtein: 100,
      resolvedCarbs: 0,
      resolvedFat: 0,
      updatedAt: "2026-01-01T00:00:00.000Z",
    }));
  }

  it("con 4 semanas de datos muestra el gasto medido junto al estimado", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(weighIns(1));
    vi.mocked(api.listMeals).mockResolvedValue(mealsEveryDay(2500));
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    // Peso estable → gasto = ingesta media; pesaje diario → margen ±177
    const measured = await screen.findByText((content) => content.startsWith("Gasto medido:"));
    expect(measured.textContent).toMatch(/2\.?500 ± 177 kcal\/día/);
  });

  it("con pesajes semanales el margen es demasiado grande y no lo muestra", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(weighIns(7));
    vi.mocked(api.listMeals).mockResolvedValue(mealsEveryDay(2500));
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    await screen.findByText((content) => content.startsWith("Gasto calórico diario estimado (TDEE):"));
    expect(screen.queryByText((content) => content.startsWith("Gasto medido:"))).toBeNull();
  });

  it("en definición con % de grasa, la proteína va por kg de masa magra", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([{ ...weight, bodyFatPct: 20 }]);
    vi.mocked(api.session).mockResolvedValue({
      username: "demo",
      isAdmin: false,
      calorieProfile: { ...profile, calorieGoal: "cut" },
    });
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    // 80 kg al 20 % → 64 kg de masa magra × 2,3–3,1 → 147–198 g
    expect(await screen.findByText("(2,3 – 3,1 g/kg de masa magra: 64 kg)")).toBeInTheDocument();
    expect(screen.getByText((_, el) => el?.tagName === "P" && /^147 – 198\s/.test(el.textContent ?? ""))).toBeInTheDocument();
  });

  // Profile: man, 36 years, 180 cm, 80 kg → BMR 1750; 3 × 60 min of gym, 30 min walking → PAL 1,49;
  // profile expenditure 2608 kcal/day, normal for him 2347–2869 (± 10 %).

  /** A weigh-in every day for the last `days` days, ending at 80 kg, changing `kgPerWeek`. */
  function dailyWeighIns(days: number, kgPerWeek: number): WeightDTO[] {
    return Array.from({ length: days }, (_, i) => {
      const n = days - i;
      const day = addDaysToKey(todayKey(), -n);
      return { ...weight, id: `w${n}`, measuredAt: `${day}T08:00:00`, weightKg: 80 - (kgPerWeek / 7) * (n - 1) };
    });
  }

  function meals(days: number, kcal: number): MealDTO[] {
    return Array.from({ length: days }, (_, i) => ({ ...mealsEveryDay(kcal)[0], id: `m${i}`, logDate: addDaysToKey(todayKey(), -(i + 1)) }));
  }

  it("si gastas claramente más que tu perfil, lo dice y «¿Por qué?» enseña el cálculo con tus números", async () => {
    // 2600 kcal/día perdiendo 0,7 kg/semana durante 5 semanas → 2600 + 0,1 × 7700 = 3370 ± 177.
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, -0.7));
    vi.mocked(api.listMeals).mockResolvedValue(meals(35, 2600));
    const user = userEvent.setup();
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    const notice = formatTemplate(t.expenditureCheck.noticeHigher, { gap: "750" });
    expect(await screen.findByText(notice)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: t.expenditureCheck.why }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(t.expenditureCheck.titleHigher)).toBeInTheDocument();
    // Every step with his numbers: Mifflin-St Jeor, the activity factor, energy balance.
    expect(within(dialog).getByText("10 × 80 + 6,25 × 180 − 5 × 36 + 5 = 1750 kcal")).toBeInTheDocument();
    expect(within(dialog).getByText((_, el) => el?.tagName === "P" && (el.textContent ?? "").startsWith("1750 × 1,49") && (el.textContent ?? "").includes("2608"))).toBeInTheDocument();
    expect(within(dialog).getByText((_, el) => el?.tagName === "P" && (el.textContent ?? "").startsWith("2600 − (-0,7 ÷ 7 × 7700) ≈ 3370 ± 177"))).toBeInTheDocument();
    expect(within(dialog).getByText(formatTemplate(t.expenditureCheck.conclusionHigher, { low: "3193", high: "3547", min: "2347", max: "2869" }))).toBeInTheDocument();
    expect(within(dialog).getByText(t.expenditureCheck.higherReason1)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: t.expenditureCheck.howItWorks })).toHaveAttribute("href", "/metodologia#check");
  });

  it("si gastas claramente menos, enumera las causas empezando por la comida sin registrar", async () => {
    // Peso estable con 1800 kcal/día → 1800 ± 177, muy por debajo de 2347.
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    vi.mocked(api.listMeals).mockResolvedValue(meals(35, 1800));
    const user = userEvent.setup();
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    expect(await screen.findByText(formatTemplate(t.expenditureCheck.noticeLower, { gap: "800" }))).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.why }));
    const reasons = within(await screen.findByRole("dialog")).getAllByRole("listitem");
    expect(reasons.map((li) => li.textContent)).toContain(t.expenditureCheck.lowerReason1);
  });

  it("«Ahora no» oculta el aviso, también al volver a la pantalla", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    vi.mocked(api.listMeals).mockResolvedValue(meals(35, 1800));
    const user = userEvent.setup();
    const { unmount } = render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    const notice = formatTemplate(t.expenditureCheck.noticeLower, { gap: "800" });
    await screen.findByText(notice);
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.notNow }));
    expect(screen.queryByText(notice)).toBeNull();

    unmount();
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);
    await screen.findByText((content) => content.startsWith("Gasto medido:"));
    expect(screen.queryByText(notice)).toBeNull();
  });

  it("si el gasto medido cabe en el ±10 % del perfil, no dice nada", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    vi.mocked(api.listMeals).mockResolvedValue(meals(35, 2500));
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    await screen.findByText((content) => content.startsWith("Gasto medido:"));
    expect(screen.queryByRole("button", { name: t.expenditureCheck.why })).toBeNull();
  });

  it("si registras las comidas pero faltan pesajes, lo dice una vez", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(weighIns(7));
    vi.mocked(api.listMeals).mockResolvedValue(mealsEveryDay(2500));
    const user = userEvent.setup();
    render(<NutritionRecommendationsCard dailyCalories={2000} dailyProtein={50} />);

    expect(await screen.findByText(t.expenditureCheck.weighInHint)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.why }));
    // Weekly weigh-ins: 1,96 × 0,5 / √245 × 7700 = ±482, above the ±300 needed.
    expect(within(await screen.findByRole("dialog")).getByText(formatTemplate(t.expenditureCheck.missingMargin, { margin: "482" }))).toBeInTheDocument();
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.notNow }));
    expect(screen.queryByText(t.expenditureCheck.weighInHint)).toBeNull();
  });

  it("un día pasado conserva el rango que tenía antes de cambiar la actividad", async () => {
    const yesterday = addDaysToKey(todayKey(), -1);
    const before = { ...profile, gymDaysPerWeek: 1, walkingMinutesPerDay: 10 };
    vi.mocked(api.session).mockResolvedValue({
      username: "demo",
      isAdmin: false,
      calorieProfile: profile,
      profileHistory: [{ ...before, until: yesterday }],
    });
    const year = Number(todayKey().slice(0, 4));
    const range = (p: CalorieProfile) => {
      const rec = calculateCalorieRecommendation(p, 80, year)!;
      return `${formatNumber(rec.targetMin)} – ${formatNumber(rec.targetMax)}`;
    };

    const { unmount } = render(
      <NutritionRecommendationsCard day={yesterday} dailyCalories={2000} dailyProtein={50} />,
    );
    expect(await screen.findByText((content) => content.startsWith(range(before)))).toBeInTheDocument();
    expect(screen.getByTestId("past-day-targets")).toHaveTextContent(t.calorias.pastDayTargets);
    unmount();

    render(<NutritionRecommendationsCard day={todayKey()} dailyCalories={2000} dailyProtein={50} />);
    expect(await screen.findByText((content) => content.startsWith(range(profile)))).toBeInTheDocument();
    expect(screen.queryByTestId("past-day-targets")).toBeNull();
  });

  it("sin historial, un día pasado usa el perfil actual y no dice nada más", async () => {
    render(
      <NutritionRecommendationsCard day={addDaysToKey(todayKey(), -3)} dailyCalories={2000} dailyProtein={50} />,
    );
    await screen.findByText(/te faltan \d+–\d+ kcal/);
    expect(screen.queryByTestId("past-day-targets")).toBeNull();
  });
});
