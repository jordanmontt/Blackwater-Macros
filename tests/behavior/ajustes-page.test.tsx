import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AjustesPage from "@/app/ajustes/page";
import { t } from "@/i18n";
import { clearCache } from "@/lib/client-cache";
import type { CalorieProfile, MealTemplateDTO } from "@/lib/core/types";
import { emptyCalorieProfile, templateDto } from "../helpers/repos";

/**
 * Requisitos visibles en la pantalla "Ajustes":
 *  - cambia el tema (claro / oscuro / sistema),
 *  - enlaza a "Perfil" (objetivo y datos corporales viven allí),
 *  - exportar comidas y peso apunta a la API (CSV); importar un CSV lo lee y lo envía,
 *  - crea, edita y borra plantillas (el formulario es "@/components/meals/template-form"),
 *    en una sección plegada por defecto (con muchas plantillas la lista sería enorme),
 *  - cierra la sesión de forma explícita.
 */

const routerMock = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }));
const themeMock = vi.hoisted(() => ({ theme: "system", setTheme: vi.fn() }));
const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("sonner", () => ({ toast: toastMock }));

const profile = (overrides: Partial<CalorieProfile> = {}): CalorieProfile => ({
  ...emptyCalorieProfile(),
  ...overrides,
});

const template = (overrides: Partial<MealTemplateDTO> = {}): MealTemplateDTO =>
  templateDto({
    ingredients: [{ name: "Avena" }],
    resolvedCalories: 250,
    resolvedProtein: 8.5,
    resolvedCarbs: 32,
    resolvedFat: 10,
    ...overrides,
  });

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    session: vi.fn(async () => ({
      username: "ana",
      isAdmin: false,
      calorieProfile: profile({ calorieGoal: "maintain" }),
    })),
    listTemplates: vi.fn(async () => [template()]),
    listWeights: vi.fn(async () => [{ id: "w-1", measuredAt: "2026-06-15T08:00:00.000Z", weightKg: 80, bodyFatPct: null, note: null, updatedAt: "2026-06-15T08:00:00.000Z" }]),
    updateSettings: vi.fn(async (calorieProfile: CalorieProfile) => ({ calorieProfile })),
    deleteTemplate: vi.fn(async () => ({ ok: true as const })),
    createTemplate: vi.fn(),
    updateTemplate: vi.fn(),
    logout: vi.fn(async () => ({ ok: true as const })),
    importMeals: vi.fn(async () => ({ added: 0, skipped: 0 })),
    importWeights: vi.fn(async () => ({ added: 1, skipped: 1 })),
    restoreTemplate: vi.fn(async () => undefined),
  } satisfies Pick<
    typeof import("@/lib/api").api,
    | "session"
    | "listTemplates"
    | "listWeights"
    | "updateSettings"
    | "deleteTemplate"
    | "createTemplate"
    | "updateTemplate"
    | "logout"
    | "importMeals"
    | "importWeights"
    | "restoreTemplate"
  >,
  errorText: (error: unknown) => (error instanceof Error ? error.message : "error"),
  UNDO_TOAST_MS: 10_000,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

vi.mock("next-themes", () => ({
  useTheme: () => themeMock,
}));

vi.mock("@/components/meals/template-form", async () => {
  const React = await import("react");
  const savedTemplate: MealTemplateDTO = {
    id: "t-nueva",
    name: "Recién creada",
    title: "Plantilla recién creada",
    notes: null,
    entryMode: "total_only",
    ingredients: [],
    totalCalories: 400,
    totalProtein: 0,
    totalCarbs: 0,
    totalFat: 0,
    resolvedCalories: 400,
    resolvedProtein: 0,
    resolvedCarbs: 0,
    resolvedFat: 0,
    updatedAt: "2026-06-15T08:00:00.000Z",
  };
  return {
    TemplateForm: ({
      open,
      onSaved,
    }: {
      open: boolean;
      onSaved: (template: MealTemplateDTO) => void;
    }) =>
      React.createElement(
        "div",
        { "data-testid": "template-form", "data-open": String(open) },
        React.createElement(
          "button",
          { type: "button", onClick: () => onSaved(savedTemplate) },
          "confirmar plantilla",
        ),
      ),
    };
});

import { api } from "@/lib/api";

describe("pantalla Ajustes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
  });

  it("carga la sesión y muestra quién está conectado", async () => {
    render(<AjustesPage />);
    expect(await screen.findByText("Sesión iniciada como ana")).toBeInTheDocument();
    expect(vi.mocked(api.listTemplates)).toHaveBeenCalledTimes(1);
  });

  it("cambia el tema al pulsar 'Oscuro'", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    await user.click(await screen.findByRole("button", { name: t.ajustes.themeDark }));
    expect(themeMock.setTheme).toHaveBeenCalledWith("dark");
  });

  it("enlaza a la página de perfil (objetivo y datos corporales)", async () => {
    render(<AjustesPage />);
    const link = await screen.findByRole("button", { name: t.perfil.open });
    expect(link).toHaveAttribute("href", "/ajustes/perfil");
    expect(screen.queryByText(t.ajustes.goalSection)).not.toBeInTheDocument();
  });

  it("la IA se configura en su propia página, como en Android", async () => {
    render(<AjustesPage />);
    const link = (await screen.findByText(t.ai.open)).closest("a");
    expect(link).toHaveAttribute("href", "/ajustes/ia");
    expect(screen.getByText(t.ai.linkSubtitle)).toBeInTheDocument();
    expect(screen.queryByLabelText(t.ai.apiKey)).toBeNull();
  });

  it("los botones de exportación apuntan a la API en CSV", async () => {
    render(<AjustesPage />);

    const mealsLink = (await screen.findByText(t.ajustes.exportMeals)).closest("a");
    expect(mealsLink).toHaveAttribute("href", "/api/export/meals");
    expect(mealsLink).toHaveAttribute("download");

    const weightsLink = screen.getByText(t.ajustes.exportWeights).closest("a");
    expect(weightsLink).toHaveAttribute("href", "/api/export/weights");
  });

  it("importar un CSV de peso lo lee, lo envía y resume el resultado", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    await screen.findByText(t.ajustes.importCsv);

    const csv = "fecha_hora,peso_kg,grasa_corporal_pct,nota\n2026-03-01T07:30:00Z,80.2,,\n2026-03-02T07:30:00Z,79.9,,\n2026-03-03T07:30:00Z,900,,\n";
    await user.upload(screen.getByTestId("import-csv-input"), new File([csv], "peso.csv", { type: "text/csv" }));

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalled());
    expect(vi.mocked(api.importWeights)).toHaveBeenCalledWith([
      { measuredAt: "2026-03-01T07:30:00.000Z", weightKg: 80.2, bodyFatPct: null, note: null },
      { measuredAt: "2026-03-02T07:30:00.000Z", weightKg: 79.9, bodyFatPct: null, note: null },
    ]);
    expect(toastMock.success).toHaveBeenCalledWith("1 pesajes importados · 1 ya estaban · 1 filas no válidas");
  });

  it("un CSV que no es de la app no envía nada", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    await screen.findByText(t.ajustes.importCsv);
    await user.upload(screen.getByTestId("import-csv-input"), new File(["nombre,apellido\nAna,Pérez"], "otro.csv"));
    await vi.waitFor(() => expect(toastMock.error).toHaveBeenCalledWith(t.ajustes.importUnknown));
    expect(vi.mocked(api.importWeights)).not.toHaveBeenCalled();
    expect(vi.mocked(api.importMeals)).not.toHaveBeenCalled();
  });

  it("las plantillas empiezan plegadas y se despliegan al tocar el título", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    const header = await screen.findByRole("button", { name: new RegExp(`^${t.hoy.templates}`) });
    expect(header).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("button", { name: t.meal.delete })).toBeNull();
    await user.click(header);
    expect(header).toHaveAttribute("aria-expanded", "true");
    expect(await screen.findByRole("button", { name: t.meal.delete })).toBeInTheDocument();
  });

  it("borra una plantilla tras confirmarlo y ofrece deshacerlo", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    await user.click(await screen.findByRole("button", { name: new RegExp(`^${t.hoy.templates}`) }));
    await user.click(await screen.findByRole("button", { name: t.meal.delete }));

    // Primero se pregunta; nada se borra todavía.
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByText(t.ajustes.deleteTemplateTitle)).toBeInTheDocument();
    expect(vi.mocked(api.deleteTemplate)).not.toHaveBeenCalled();

    // Tras borrar, el servidor ya no devuelve la plantilla: la lista se
    // revalida desde la (mockeada) base de datos.
    vi.mocked(api.listTemplates).mockResolvedValue([] as MealTemplateDTO[]);
    await user.click(within(dialog).getByRole("button", { name: t.meal.delete }));
    expect(vi.mocked(api.deleteTemplate)).toHaveBeenCalledWith("t-1");
    expect(await screen.findByText(t.hoy.noTemplates)).toBeInTheDocument();

    // El aviso ofrece «Deshacer», que devuelve la misma plantilla.
    const [message, options] = toastMock.success.mock.calls.at(-1)!;
    expect(message).toBe(t.ajustes.templateDeleted);
    (options as { action: { onClick: () => void } }).action.onClick();
    await vi.waitFor(() =>
      expect(vi.mocked(api.restoreTemplate)).toHaveBeenCalledWith(expect.objectContaining({ id: "t-1" })),
    );
  });

  it("abre el formulario de nueva plantilla y al guardar la añade a la lista", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    await user.click(await screen.findByRole("button", { name: new RegExp(`^${t.hoy.templates}`) }));

    // El formulario apunta al widget real (mockeado). Al guardar, la lista se
    // refresca desde la (mockeada) base de datos y aparece la nueva plantilla.
    vi.mocked(api.listTemplates).mockResolvedValue([
      template(),
      {
        id: "t-nueva",
        name: "Recién creada",
        title: "Plantilla recién creada",
        notes: null,
        entryMode: "total_only",
        ingredients: [],
        totalCalories: 400,
        totalProtein: 0,
        totalCarbs: 0,
        totalFat: 0,
        resolvedCalories: 400,
        resolvedProtein: 0,
        resolvedCarbs: 0,
        resolvedFat: 0,
        updatedAt: "2026-06-15T08:00:00.000Z",
      },
    ]);

    await user.click(await screen.findByRole("button", { name: t.ajustes.newTemplate }));
    const stub = screen.getByTestId("template-form");
    expect(stub).toHaveAttribute("data-open", "true");

    await user.click(within(stub).getByRole("button", { name: "confirmar plantilla" }));
    expect(await screen.findByText("Plantilla recién creada")).toBeInTheDocument();
  });

  it("cierra la sesión y navega a /login", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);

    await user.click(await screen.findByRole("button", { name: t.auth.logout }));
    expect(vi.mocked(api.logout)).toHaveBeenCalledTimes(1);
    expect(routerMock.replace).toHaveBeenCalledWith("/login");
  });

  it("oculta la sección de administración para usuarios normales", async () => {
    render(<AjustesPage />);
    await screen.findByText("Sesión iniciada como ana");
    expect(screen.queryByText(t.ajustes.administration)).not.toBeInTheDocument();
  });

  it("muestra la sección de administración solo para administradores", async () => {
    vi.mocked(api.session).mockResolvedValue({
      username: "ana",
      isAdmin: true,
      calorieProfile: profile({ calorieGoal: "maintain" }),
    });
    render(<AjustesPage />);

    const link = await screen.findByRole("button", { name: t.ajustes.openAdmin });
    expect(link).toHaveAttribute("href", "/admin");
    expect(screen.getAllByText(t.ajustes.administration).length).toBeGreaterThan(0);
  });
});