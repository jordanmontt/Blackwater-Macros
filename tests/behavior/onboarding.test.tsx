import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import BienvenidaPage from "@/app/bienvenida/page";
import { OnboardingRedirect } from "@/components/onboarding-redirect";
import { getAiSettings } from "@/lib/ai/settings";
import { clearCache } from "@/lib/client-cache";
import { needsOnboarding, onboardingDone } from "@/lib/onboarding";
import type { CalorieProfile, WeightDTO } from "@/lib/core/types";
import { t } from "@/i18n";

/**
 * Requisitos de los primeros pasos en la web (D12):
 *  - tras el primer inicio de sesión, con el perfil incompleto, Comidas lleva a
 *    /bienvenida una sola vez; con el perfil completo no,
 *  - «Tus datos» guarda el perfil (con una actividad por defecto) y el peso actual,
 *  - la IA es opcional: la clave de Google se prueba y queda solo en este navegador,
 *  - «Saltar» y «Empezar» terminan los pasos y vuelven a Comidas.
 */

const replace = vi.fn();
let pathname = "/";
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => pathname,
}));

const EMPTY: CalorieProfile = {
  gender: null,
  birthYear: null,
  heightCm: null,
  gymDaysPerWeek: null,
  gymSessionMinutes: null,
  walkingMinutesPerDay: null,
  calorieGoal: null,
};
let profile: CalorieProfile = EMPTY;

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    session: vi.fn(async () => ({ username: "ana", isAdmin: false, calorieProfile: profile })),
    listWeights: vi.fn(async () => [] as WeightDTO[]),
    updateSettings: vi.fn(async (next: CalorieProfile) => ({ calorieProfile: next })),
    createWeight: vi.fn(async () => ({}) as WeightDTO),
  },
}));

import { api } from "@/lib/api";

const fetchMock = vi.fn<typeof fetch>();

describe("primeros pasos (web)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearCache();
    localStorage.clear();
    profile = EMPTY;
    pathname = "/";
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("solo con el perfil incompleto y sin haberlos hecho", () => {
    expect(needsOnboarding(false, EMPTY)).toBe(true);
    expect(needsOnboarding(true, EMPTY)).toBe(false);
    expect(
      needsOnboarding(false, {
        gender: "male",
        birthYear: 1990,
        heightCm: 178,
        gymDaysPerWeek: 3,
        gymSessionMinutes: 60,
        walkingMinutesPerDay: 30,
        calorieGoal: "maintain",
      }),
    ).toBe(false);
  });

  it("Comidas lleva a /bienvenida si el perfil está incompleto", async () => {
    render(<OnboardingRedirect />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/bienvenida"));
  });

  it("con el perfil completo no redirige y no vuelve a preguntar", async () => {
    profile = { ...EMPTY, gender: "male", birthYear: 1990, heightCm: 178, gymDaysPerWeek: 3, gymSessionMinutes: 60, walkingMinutesPerDay: 30, calorieGoal: "cut" };
    render(<OnboardingRedirect />);
    await waitFor(() => expect(onboardingDone()).toBe(true));
    expect(replace).not.toHaveBeenCalled();
  });

  it("fuera de Comidas no hace nada", () => {
    pathname = "/login";
    render(<OnboardingRedirect />);
    expect(vi.mocked(api.session)).not.toHaveBeenCalled();
  });

  it("guarda tus datos, prueba la clave de la IA y termina", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "OK" }] } }] })));
    const user = userEvent.setup();
    render(<BienvenidaPage />);

    const next = screen.getByRole("button", { name: t.onboarding.next });
    expect(next).toBeDisabled();
    await user.click(within(screen.getByRole("group", { name: t.calorias.genderLabel })).getByRole("button", { name: t.calorias.genderFemale }));
    await user.type(screen.getByLabelText(t.calorias.birthYearLabel), "1992");
    await user.type(screen.getByLabelText(t.calorias.heightLabel), "165");
    await user.type(screen.getByLabelText(t.onboarding.weight), "62,5");
    await user.click(within(screen.getByRole("group", { name: t.onboarding.goal })).getByRole("button", { name: t.ajustes.goalCut }));
    await user.click(next);

    await waitFor(() =>
      expect(vi.mocked(api.updateSettings)).toHaveBeenCalledWith({
        gender: "female",
        birthYear: 1992,
        heightCm: 165,
        gymDaysPerWeek: 0,
        gymSessionMinutes: 60,
        walkingMinutesPerDay: 30,
        calorieGoal: "cut",
      }),
    );
    expect(vi.mocked(api.createWeight)).toHaveBeenCalledWith(expect.objectContaining({ weightKg: 62.5 }));

    await screen.findByText(t.onboarding.aiTitle);
    expect(screen.getByRole("button", { name: t.onboarding.next })).toBeDisabled();
    await user.type(screen.getByLabelText(t.onboarding.aiKey), "fake-key");
    await user.click(screen.getByRole("button", { name: t.ai.test }));
    expect(await screen.findByText(t.ai.testOk)).toBeInTheDocument();
    expect(getAiSettings()).toMatchObject({ provider: "gemini", apiKeys: { gemini: "fake-key" } });
    await user.click(screen.getByRole("button", { name: t.onboarding.next }));

    expect(await screen.findByText(t.onboarding.doneAi)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.onboarding.start }));
    expect(onboardingDone()).toBe(true);
    expect(replace).toHaveBeenCalledWith("/");
  });

  it("«Saltar» termina los pasos sin guardar nada", async () => {
    const user = userEvent.setup();
    render(<BienvenidaPage />);
    await user.click(screen.getByRole("button", { name: t.onboarding.skip }));
    expect(onboardingDone()).toBe(true);
    expect(replace).toHaveBeenCalledWith("/");
    expect(vi.mocked(api.updateSettings)).not.toHaveBeenCalled();
  });
});
