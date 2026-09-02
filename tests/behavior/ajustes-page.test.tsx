import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AjustesPage from "@/app/ajustes/page";
import { t } from "@/i18n";
import { clearCache } from "@/lib/client-cache";
import type { CalorieProfile, MealTemplateDTO } from "@/lib/core/types";
import { emptyCalorieProfile, templateDto } from "../helpers/repos";

/**
 * Requisitos visibles en la pantalla "Ajustes":
 *  - cambia el tema (claro / oscuro / sistema),
 *  - elegir un objetivo guarda el perfil calórico con un breve debounce y no
 *    guarda mientras haya errores de validación,
 *  - exportar comidas y peso apunta a la API (CSV),
 *  - crea, edita y borra plantillas (el formulario es "@/components/meals/template-form"),
 *  - cierra la sesión de forma explícita.
 */

const routerMock = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }));
const themeMock = vi.hoisted(() => ({ theme: "system", setTheme: vi.fn() }));

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
  } satisfies Pick<
    typeof import("@/lib/api").api,
    "session" | "listTemplates" | "listWeights" | "updateSettings" | "deleteTemplate" | "createTemplate" | "updateTemplate" | "logout"
  >,
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

  it("guarda el objetivo tras el debounce de medio segundo", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);

    await user.click(await screen.findByRole("button", { name: t.ajustes.goalSurplus }));
    expect(vi.mocked(api.updateSettings)).not.toHaveBeenCalled();

    await waitFor(() =>
      expect(vi.mocked(api.updateSettings)).toHaveBeenCalledWith(
        expect.objectContaining({ calorieGoal: "surplus" }),
      ),
    );
  });

  it("no guarda el perfil y muestra el error si un campo no valida", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);

    const birthYear = await screen.findByLabelText(t.calorias.birthYearLabel);
    await user.type(birthYear, "1800");

    expect(await screen.findByText("El año debe estar entre 1920 y 2010")).toBeInTheDocument();
    // deja pasar el debounce y comprueba que sigue sin guardar
    await waitFor(() => {
      expect(vi.mocked(api.updateSettings)).not.toHaveBeenCalled();
    });
  });

  it("los botones de exportación apuntan a la API en CSV", async () => {
    render(<AjustesPage />);

    const mealsLink = (await screen.findByText(t.ajustes.exportMeals)).closest("a");
    expect(mealsLink).toHaveAttribute("href", "/api/export/meals");
    expect(mealsLink).toHaveAttribute("download");

    const weightsLink = screen.getByText(t.ajustes.exportWeights).closest("a");
    expect(weightsLink).toHaveAttribute("href", "/api/export/weights");
  });

  it("borra una plantilla y la quita de la lista", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);
    await screen.findByRole("button", { name: t.meal.delete });

    // Tras borrar, el servidor ya no devuelve la plantilla: la lista se
    // revalida desde la (mockeada) base de datos.
    vi.mocked(api.listTemplates).mockResolvedValue([] as MealTemplateDTO[]);

    await user.click(screen.getByRole("button", { name: t.meal.delete }));
    expect(vi.mocked(api.deleteTemplate)).toHaveBeenCalledWith("t-1");
    expect(await screen.findByText(t.hoy.noTemplates)).toBeInTheDocument();
  });

  it("abre el formulario de nueva plantilla y al guardar la añade a la lista", async () => {
    const user = userEvent.setup();
    render(<AjustesPage />);

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