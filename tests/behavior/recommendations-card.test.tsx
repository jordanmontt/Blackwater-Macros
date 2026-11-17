import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { formatTemplate, t } from "@/i18n";
import { DaySummaryCard } from "@/components/nutrition-recommendations";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import type { CalorieProfile, MealDTO, WeightDTO } from "@/lib/core/types";
import { calculateCalorieRecommendation } from "@/lib/core/calories";
import { buildStatsFromData } from "@/lib/core/stats-builder";
import { formatNumber } from "@/i18n/format";

/**
 * Requisitos visibles en las tarjetas de recomendación (calorías y proteína)
 * de la pantalla "Hoy":
 *  - la cantidad que falta para alcanzar el objetivo se muestra como un rango
 *    ("te faltan X–Y"), no como un número exacto,
 *  - la tarjeta dice lo esencial (barras, lo que falta, hidratos y grasa); de dónde
 *    sale el objetivo (TMB, TDEE, gasto medido, proteína por kg) está en «¿Por qué
 *    estos números?», donde el número grande es el promedio estimado del rango,
 *  - si la ingesta está dentro del rango se indica "en rango",
 *  - si se excede se muestra un valor ("te pasaste de X"); en proteína, pasarse
 *    no es un problema: se dice en gris y la barra no cambia de color,
 *  - con 4 semanas de comidas y pesajes frecuentes aparece el gasto medido
 *    con su margen, y el objetivo se calcula con él mezclado con el del perfil;
 *    con pesajes semanales no aparece,
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
    stats: vi.fn(),
    session: vi.fn(),
  } satisfies Pick<typeof import("@/lib/api").api, "listWeights" | "stats" | "session">,
}));

import { api } from "@/lib/api";
import { clearCache } from "@/lib/client-cache";

/** The Comidas summary with the day's totals (carbs and fat don't matter here). */
function card(calories: number, protein: number, day?: string) {
  return <DaySummaryCard day={day} totals={{ calories, protein, carbs: 0, fat: 0 }} />;
}

/** «¿Por qué estos números?»: the sheet with where the ranges come from. */
async function openWhy(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: t.hoy.whyNumbers }));
  return screen.findByRole("dialog", { name: t.hoy.whyNumbers });
}

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

/** The card reads every day's kcal from the all-time stats, as the server builds them (meals in date order). */
function logMeals(meals: MealDTO[]) {
  const sorted = [...meals].sort((a, b) => a.logDate.localeCompare(b.logDate));
  vi.mocked(api.stats).mockImplementation(async (range, today) => buildStatsFromData(sorted, [], range, today));
}

describe("resumen del día (Comidas)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    localStorage.clear();
    // A Monday: this week's measurement reads the 4 weeks before today.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 2, 2, 12));
    vi.mocked(api.listWeights).mockResolvedValue([weight]);
    logMeals([]);
    vi.mocked(api.session).mockResolvedValue({
      username: "demo",
      isAdmin: false,
      calorieProfile: profile,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("muestra la cantidad que falta como un rango, no un número exacto", async () => {
    // Ingesta 2000 kcal, objetivo rango (p. ej. 2200–2400) → "te faltan 200–400 kcal"
    render(card(2000, 50));

    const missing = await screen.findByText(/te faltan \d+–\d+ kcal/);
    expect(missing.textContent).toMatch(/te faltan \d+–\d+ kcal/);
    expect(missing.textContent).not.toMatch(/te faltan \d+ kcal/);
  });

  it("en «¿Por qué estos números?» el número grande va con la etiqueta de promedio estimado", async () => {
    const user = userEvent.setup();
    render(card(2000, 50));

    // la etiqueta aparece dentro de un párrafo con el rango → matcher de substring
    const why = await openWhy(user);
    const labels = within(why).getAllByText((content) => content.includes("Promedio estimado"));
    expect(labels.length).toBeGreaterThanOrEqual(2);
  });

  it("indica 'en rango' cuando la ingesta está dentro del objetivo", async () => {
    render(card(2700, 110));

    expect(await screen.findAllByText("en rango")).not.toHaveLength(0);
  });

  it("indica cuánto se ha pasado como un único valor (diferencia con el máximo)", async () => {
    render(card(3000, 140));

    const exceeded = await screen.findByText(/te pasaste de \d+ kcal/);
    expect(exceeded.textContent).toMatch(/te pasaste de \d+ kcal/);
    expect(exceeded.textContent).not.toMatch(/te pasaste de \d+–\d+ kcal/);
  });

  it("la tarjeta dice lo esencial; de dónde sale el objetivo (TMB, TDEE) está a un toque", async () => {
    const user = userEvent.setup();
    render(card(2000, 50));

    // Message first: the bars and what is missing; the day's meals start on the first screen.
    await screen.findByText(/te faltan \d+–\d+ kcal/);
    expect(screen.queryByText(t.calorias.bmr)).toBeNull();
    const why = await openWhy(user);
    expect(within(why).getByText(t.calorias.bmr)).toBeInTheDocument();
    expect(within(why).getByText(t.calorias.tdee)).toBeInTheDocument();
    expect(within(why).getByRole("link", { name: t.hoy.fullMethodology })).toHaveAttribute("href", "/metodologia");
  });

  it("los hidratos y la grasa del día se ven como totales, sin objetivo", async () => {
    render(<DaySummaryCard totals={{ calories: 2000, protein: 50, carbs: 217, fat: 89.5 }} />);
    await screen.findByText(/te faltan \d+–\d+ kcal/);
    expect(screen.getByText((_, el) => el?.tagName === "SPAN" && el.textContent === `${t.hoy.carbs} 217 g`)).toBeInTheDocument();
    expect(screen.getByText((_, el) => el?.tagName === "SPAN" && el.textContent === `${t.hoy.fat} 89,5 g`)).toBeInTheDocument();
  });

  it("la barra muestra lo consumido frente al objetivo y cambia de color al pasarse", async () => {
    const { rerender } = render(card(1200, 50));
    const bars = await screen.findAllByRole("progressbar");
    expect(bars[0]).toHaveAttribute("aria-valuenow", "1200");
    expect(screen.getAllByTestId("intake-fill")[0].className).toContain("bg-primary");
    // "consumido / mínimo–máximo kcal" en palabras, además de la barra
    expect(screen.getByText((_, el) => el?.tagName === "SPAN" && /^1\.?200 \/ [\d.]+–[\d.]+ kcal$/.test(el.textContent ?? ""))).toBeInTheDocument();

    rerender(card(4000, 50));
    expect(screen.getAllByTestId("intake-fill")[0].className).toContain("bg-tertiary");
  });

  it("pasarse de proteína no es un aviso: se dice en gris y la barra no cambia de color", async () => {
    // Mantenimiento con gimnasio, 80 kg: 1,6–2,2 g/kg → 128–176 g; 200 g son 24 g por encima.
    render(card(2000, 200));

    const above = await screen.findByText(formatTemplate(t.protein.aboveRangeBy, { min: "24" }));
    expect(above.className).toContain("text-muted-foreground");
    expect(screen.queryByText(/te pasaste de \d+ g/)).toBeNull();
    expect(screen.getAllByTestId("intake-fill")[1].className).toContain("bg-primary");
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
    logMeals(mealsEveryDay(2500));
    const user = userEvent.setup();
    render(card(2000, 50));

    // Peso estable → gasto = ingesta media; pesaje diario → margen ±177
    const why = await openWhy(user);
    expect(within(why).getByText(t.calorias.measuredTdee)).toBeInTheDocument();
    expect(within(why).getByText(/2\.?500 ± 177 kcal\/día/)).toBeInTheDocument();
  });

  it("con pesajes semanales el margen es demasiado grande y no lo muestra", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(weighIns(7));
    logMeals(mealsEveryDay(2500));
    const user = userEvent.setup();
    render(card(2000, 50));

    const why = await openWhy(user);
    expect(within(why).getByText(t.calorias.tdee)).toBeInTheDocument();
    expect(within(why).queryByText(t.calorias.measuredTdee)).toBeNull();
  });

  it("en definición con % de grasa, la proteína va por kg de masa magra", async () => {
    vi.mocked(api.listWeights).mockResolvedValue([{ ...weight, bodyFatPct: 20 }]);
    vi.mocked(api.session).mockResolvedValue({
      username: "demo",
      isAdmin: false,
      calorieProfile: { ...profile, calorieGoal: "cut" },
    });
    const user = userEvent.setup();
    render(card(2000, 50));

    // 80 kg al 20 % → 64 kg de masa magra × 2,3–3,1 → 147–198 g
    const why = await openWhy(user);
    expect(within(why).getByText("(2,3 – 3,1 g/kg de masa magra: 64 kg)")).toBeInTheDocument();
    expect(within(why).getByText((_, el) => el?.tagName === "P" && /^147 – 198\s/.test(el.textContent ?? ""))).toBeInTheDocument();
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
    logMeals(meals(35, 2600));
    const user = userEvent.setup();
    render(card(2000, 50));

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

  it("con gasto medido, el objetivo se calcula con él y lo dice, enlazando a cómo se hace", async () => {
    // Perfil 2608 ± 10 %, medido 3370 ± 177: los datos pesan un 89 % → 3288; mantenimiento 3188–3388.
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, -0.7));
    logMeals(meals(35, 2600));
    const user = userEvent.setup();
    render(card(2000, 50));

    const why = await openWhy(user);
    const used = within(why).getByRole("link", { name: t.calorias.targetExpenditure });
    expect(used).toHaveAttribute("href", "/metodologia#adaptive");
    expect(used.closest("div")).toHaveTextContent(formatTemplate(t.calorias.targetExpenditureValue, { n: "3288", share: "89" }));
    expect(within(why).getByText((_, el) => el?.tagName === "P" && /^3188 – 3388\s/.test(el.textContent ?? ""))).toBeInTheDocument();
    // The profile's own expenditure is still shown next to it.
    expect(within(why).getByText(t.calorias.tdee).closest("div")).toHaveTextContent("2608");
  });

  it("sin gasto medido, el objetivo sale solo del perfil y no hay línea de gasto usado", async () => {
    const user = userEvent.setup();
    render(card(2000, 50));
    const why = await openWhy(user);
    expect(within(why).queryByText(t.calorias.targetExpenditure)).toBeNull();
  });

  it("si gastas claramente menos, enumera las causas empezando por la comida sin registrar", async () => {
    // Peso estable con 1800 kcal/día → 1800 ± 177, muy por debajo de 2347.
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    logMeals(meals(35, 1800));
    const user = userEvent.setup();
    render(card(2000, 50));

    // He trains: part of the gap can be muscle gained while losing fat.
    const notice = `${formatTemplate(t.expenditureCheck.noticeLower, { gap: "800" })} ${t.expenditureCheck.noticeLowerTraining}`;
    expect(await screen.findByText(notice)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.why }));
    const reasons = within(await screen.findByRole("dialog")).getAllByRole("listitem");
    expect(reasons.map((li) => li.textContent)).toContain(t.expenditureCheck.lowerReason1);
    expect(reasons.map((li) => li.textContent)).toContain(t.expenditureCheck.lowerReasonTraining);
  });

  it("sin gimnasio, el aviso de gastar menos no habla de músculo", async () => {
    vi.mocked(api.session).mockResolvedValue({
      username: "demo",
      isAdmin: false,
      calorieProfile: { ...profile, gymDaysPerWeek: 0 },
    });
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    logMeals(meals(35, 1800));
    const user = userEvent.setup();
    render(card(2000, 50));

    expect(await screen.findByText(formatTemplate(t.expenditureCheck.noticeLower, { gap: "750" }))).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.why }));
    const reasons = within(await screen.findByRole("dialog")).getAllByRole("listitem");
    expect(reasons.map((li) => li.textContent)).not.toContain(t.expenditureCheck.lowerReasonTraining);
  });

  it("«Ahora no» oculta el aviso, también al volver a la pantalla", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    logMeals(meals(35, 1800));
    const user = userEvent.setup();
    const { unmount } = render(card(2000, 50));

    const notice = `${formatTemplate(t.expenditureCheck.noticeLower, { gap: "800" })} ${t.expenditureCheck.noticeLowerTraining}`;
    await screen.findByText(notice);
    await user.click(screen.getByRole("button", { name: t.expenditureCheck.notNow }));
    expect(screen.queryByText(notice)).toBeNull();

    unmount();
    render(card(2000, 50));
    await screen.findByRole("button", { name: t.hoy.whyNumbers });
    expect(screen.queryByText(notice)).toBeNull();
  });

  it("si el gasto medido cabe en el ±10 % del perfil, no dice nada", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(dailyWeighIns(35, 0));
    logMeals(meals(35, 2500));
    const user = userEvent.setup();
    render(card(2000, 50));

    // The measurement is there (in «¿Por qué estos números?»), and the card says nothing about it.
    const why = await openWhy(user);
    expect(within(why).getByText(t.calorias.measuredTdee)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: t.expenditureCheck.why })).toBeNull();
  });

  it("si registras las comidas pero faltan pesajes, lo dice una vez", async () => {
    vi.mocked(api.listWeights).mockResolvedValue(weighIns(7));
    logMeals(mealsEveryDay(2500));
    const user = userEvent.setup();
    render(card(2000, 50));

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
    // The bar's «eaten / min–max kcal».
    const range = (p: CalorieProfile) => {
      const rec = calculateCalorieRecommendation(p, 80, year)!;
      return `2000 / ${formatNumber(rec.targetMin)}–${formatNumber(rec.targetMax)} kcal`;
    };
    const bar = (text: string) => (_: string, el: Element | null) => el?.tagName === "SPAN" && el.textContent === text;

    const { unmount } = render(
      card(2000, 50, yesterday),
    );
    expect(await screen.findByText(bar(range(before)))).toBeInTheDocument();
    expect(screen.getByTestId("past-day-targets")).toHaveTextContent(t.calorias.pastDayTargets);
    unmount();

    render(card(2000, 50, todayKey()));
    expect(await screen.findByText(bar(range(profile)))).toBeInTheDocument();
    expect(screen.queryByTestId("past-day-targets")).toBeNull();
  });

  it("sin historial, un día pasado usa el perfil actual y no dice nada más", async () => {
    render(
      card(2000, 50, addDaysToKey(todayKey(), -3)),
    );
    await screen.findByText(/te faltan \d+–\d+ kcal/);
    expect(screen.queryByTestId("past-day-targets")).toBeNull();
  });
});
