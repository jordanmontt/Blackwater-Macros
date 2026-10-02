import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HoyPage from "@/app/page";
import { addDaysToKey, todayKey } from "@/lib/core/dates";
import { formatNumber } from "@/i18n/format";
import type { MealDTO, MealTemplateDTO } from "@/lib/core/types";

/**
 * Requisitos visibles en la pantalla "Hoy":
 *  - el usuario ve el total acumulado de calorías y proteína del día,
 *    calculado con todas las comidas registradas,
 *  - puede avanzar/retroceder de día para revisar comidas pasadas,
 *  - si no hay comidas, la pantalla lo deja claro e invita a añadir,
 *  - el botón + abre «Añadir comida»: escribir a mano, copiar de otro día o
 *    aplicar una plantilla (sin poder dispararla dos veces sin querer),
 *  - borrar una comida pregunta antes y luego ofrece «Deshacer», que la devuelve
 *    a su sitio,
 *  - el formulario se cierra al arrastrarlo/pulsar fuera, pero si hay cambios
 *    pregunta antes de descartarlos.
 */

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("sonner", () => ({ toast: toastMock }));

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    listMeals: vi.fn(async () => [
      meal({ title: "Desayuno", kcal: 475, protein: 32.4 }),
      meal({ title: "Comida", kcal: 850, protein: 45 }),
    ]),
    listTemplates: vi.fn(async () => [] as MealTemplateDTO[]),
    createMeal: vi.fn(async () => meal({ title: "nueva", kcal: 0, protein: 0 })),
    reorderMeals: vi.fn(async () => ({ ok: true as const })),
    deleteMeal: vi.fn(async () => ({ ok: true as const })),
    restoreMeal: vi.fn(async () => undefined),
    listWeights: vi.fn(async () => []),
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
    "listMeals" | "listTemplates" | "createMeal" | "reorderMeals" | "deleteMeal" | "restoreMeal" | "listWeights" | "session"
  >,
  errorText: (error: unknown) => (error instanceof Error ? error.message : "error"),
  UNDO_TOAST_MS: 10_000,
}));

import { api } from "@/lib/api";
import { clearCache } from "@/lib/client-cache";
import { t } from "@/i18n";

describe("pantalla Hoy", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    sessionStorage.clear();
    vi.mocked(api.listTemplates).mockResolvedValue([]);
    vi.mocked(api.listMeals).mockImplementation(async () => [
      meal({ title: "Desayuno", kcal: 475, protein: 32.4 }),
      meal({ title: "Comida", kcal: 850, protein: 45 }),
    ]);
  });

  it("muestra los totales diarios sumando todas las comidas del día", async () => {
    render(<HoyPage />);

    // 475 + 850 = 1325 kcal; 32.4 + 45 = 77.4 g
    expect(await screen.findByText(formatNumber(1325))).toBeInTheDocument();
    expect(screen.getByText(formatNumber(77.4, 1))).toBeInTheDocument();
    expect(screen.getByText("Desayuno")).toBeInTheDocument();
    expect(screen.getByText("Comida")).toBeInTheDocument();
  });

  it("cada comida muestra sus propias calorías y proteína", async () => {
    render(<HoyPage />);

    // el valor aparece tanto en el resumen de ingredientes como en la insignia de la comida
    expect((await screen.findAllByText("475 kcal")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("850 kcal").length).toBeGreaterThan(0);
    expect(screen.getByText("32 g · Proteína")).toBeInTheDocument(); // redondeo visible
  });

  it("al cambiar de día se cargan las comidas de ese otro día", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await screen.findByText("Desayuno");

    await user.click(screen.getByRole("button", { name: "Día siguiente" }));

    expect(api.listMeals).toHaveBeenLastCalledWith(expect.any(String), expect.any(String));
    expect(vi.mocked(api.listMeals).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  async function openAddSheet(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByText("Desayuno");
    await user.click(screen.getAllByRole("button", { name: t.hoy.addMeal })[0]);
    return screen.findByRole("dialog", { name: t.addFood.title });
  }

  it("aplicar una plantilla desde «Añadir comida» añade la comida y bloquea el botón mientras tanto", async () => {
    const user = userEvent.setup();
    let resolveCreate!: (value: MealDTO) => void;
    vi.mocked(api.createMeal).mockImplementationOnce(
      () => new Promise<MealDTO>((resolve) => (resolveCreate = resolve)),
    );
    vi.mocked(api.listTemplates).mockResolvedValue([template({ name: "Desayuno salvaje" })]);
    render(<HoyPage />);

    const sheet = await openAddSheet(user);
    const chip = await within(sheet).findByRole("button", { name: "Desayuno salvaje" });
    await user.click(chip);

    expect(vi.mocked(api.createMeal)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api.createMeal)).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Desayuno salvaje", entryMode: "per_ingredient", logDate: todayKey() }),
    );
    // Mientras la petición vuela no se puede re-disparar.
    expect(chip).toBeDisabled();

    resolveCreate(meal({ title: "Desayuno salvaje", kcal: 300, protein: 20 }));
    // Termina: el panel se cierra y la lista se refresca.
    await waitForDialogToClose(t.addFood.title);
    expect(vi.mocked(api.listMeals).mock.calls.length).toBeGreaterThan(1);
  });

  it("aplicar una plantilla 'solo total' crea la comida con sus macros totales", async () => {
    const user = userEvent.setup();
    vi.mocked(api.listTemplates).mockResolvedValue([
      {
        id: "tpl-cena",
        name: "Cena ligera",
        title: "Cena ligera",
        notes: null,
        entryMode: "total_only",
        ingredients: [],
        totalCalories: 380,
        totalProtein: 30,
        totalCarbs: 30,
        totalFat: 15,
        resolvedCalories: 380,
        resolvedProtein: 30,
        resolvedCarbs: 30,
        resolvedFat: 15,
        updatedAt: "2026-08-23T08:00:00.000Z",
      } satisfies MealTemplateDTO,
    ]);
    render(<HoyPage />);

    const sheet = await openAddSheet(user);
    await user.click(await within(sheet).findByRole("button", { name: "Cena ligera" }));

    expect(vi.mocked(api.createMeal)).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "Cena ligera",
        entryMode: "total_only",
        totalCalories: 380,
        totalProtein: 30,
        totalCarbs: 30,
        totalFat: 15,
      }),
    );
  });

  it("borra una comida tras confirmar y «Deshacer» la devuelve a su sitio", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);
    await screen.findByText("Desayuno");

    await user.click(screen.getAllByRole("button", { name: t.meal.delete })[0]);
    const confirm = await screen.findByRole("alertdialog");
    expect(vi.mocked(api.deleteMeal)).not.toHaveBeenCalled();
    await user.click(within(confirm).getByRole("button", { name: t.meal.delete }));
    expect(vi.mocked(api.deleteMeal)).toHaveBeenCalledWith("meal-Desayuno");

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    const [message, options] = toastMock.success.mock.calls.at(-1)!;
    expect(message).toBe(t.meal.deleted);
    (options as { action: { onClick: () => void } }).action.onClick();
    await vi.waitFor(() =>
      expect(vi.mocked(api.restoreMeal)).toHaveBeenCalledWith(
        expect.objectContaining({ id: "meal-Desayuno" }),
        ["meal-Desayuno", "meal-Comida"],
      ),
    );
  });

  it("«Escribir a mano» abre el formulario vacío", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);

    const sheet = await openAddSheet(user);
    await user.click(within(sheet).getByRole("button", { name: new RegExp(t.addFood.manual) }));

    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    expect(within(form).getByLabelText(t.meal.titleLabel)).toHaveValue("");
  });

  it("copia comidas elegidas de otro día al día que se está viendo", async () => {
    const user = userEvent.setup();
    const yesterday = addDaysToKey(todayKey(), -1);
    vi.mocked(api.listMeals).mockImplementation(async (from: string) =>
      from === yesterday
        ? [
            { ...meal({ title: "Tortilla de ayer", kcal: 400, protein: 25 }), id: "y-1", logDate: yesterday },
            { ...meal({ title: "Cena de ayer", kcal: 600, protein: 40 }), id: "y-2", logDate: yesterday },
          ]
        : [meal({ title: "Desayuno", kcal: 475, protein: 32.4 })],
    );
    render(<HoyPage />);

    const sheet = await openAddSheet(user);
    await user.click(within(sheet).getByRole("button", { name: new RegExp(t.addFood.copy) }));

    const copyButton = within(sheet).getByRole("button", { name: "Copiar (0)" });
    expect(copyButton).toBeDisabled();
    await user.click(await within(sheet).findByRole("checkbox", { name: /Tortilla de ayer/ }));
    await user.click(within(sheet).getByRole("button", { name: "Copiar (1)" }));

    expect(vi.mocked(api.createMeal)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(api.createMeal)).toHaveBeenCalledWith(
      expect.objectContaining({ title: "Tortilla de ayer", logDate: todayKey() }),
    );
  });

  it("al cerrar el formulario con cambios pregunta antes de descartarlos", async () => {
    const user = userEvent.setup();
    render(<HoyPage />);

    const sheet = await openAddSheet(user);
    await user.click(within(sheet).getByRole("button", { name: new RegExp(t.addFood.manual) }));
    const form = await screen.findByRole("dialog", { name: t.meal.newTitle });
    await user.type(within(form).getByLabelText(t.meal.titleLabel), "Merienda");

    await user.click(within(form).getByRole("button", { name: t.meal.cancel }));
    const confirm = await screen.findByRole("alertdialog", { name: t.addFood.discardTitle });
    await user.click(within(confirm).getByRole("button", { name: t.addFood.keepEditing }));
    expect(within(screen.getByRole("dialog", { name: t.meal.newTitle })).getByLabelText(t.meal.titleLabel)).toHaveValue(
      "Merienda",
    );

    await user.keyboard("{Escape}");
    await user.click(
      within(await screen.findByRole("alertdialog", { name: t.addFood.discardTitle })).getByRole("button", {
        name: t.addFood.discard,
      }),
    );
    await waitForDialogToClose(t.meal.newTitle);
    expect(vi.mocked(api.createMeal)).not.toHaveBeenCalled();
  });
});

async function waitForDialogToClose(name: string) {
  const { waitFor } = await import("@testing-library/react");
  await waitFor(() => expect(screen.queryByRole("dialog", { name })).toBeNull());
}

function meal(partial: { title: string; kcal: number; protein: number }): MealDTO {
  return {
    id: `meal-${partial.title}`,
    logDate: "2026-08-23",
    title: partial.title,
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [{ name: "ingrediente", calories: partial.kcal, protein: partial.protein }],
    totalCalories: null,
    totalProtein: null,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: partial.kcal,
    resolvedProtein: partial.protein,
    resolvedCarbs: 0,
    resolvedFat: 0,
    updatedAt: "2026-08-23T08:00:00.000Z",
  };
}

function template(partial: { name: string }): MealTemplateDTO {
  return {
    id: `tpl-${partial.name}`,
    name: partial.name,
    title: partial.name,
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [{ name: "avena", calories: 150, protein: 5 }],
    totalCalories: null,
    totalProtein: null,
    totalCarbs: null,
    totalFat: null,
    resolvedCalories: 150,
    resolvedProtein: 5,
    resolvedCarbs: 0,
    resolvedFat: 0,
    updatedAt: "2026-08-23T08:00:00.000Z",
  };
}
