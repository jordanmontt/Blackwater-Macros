import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AiSettingsCard } from "@/components/settings/ai-settings-card";
import CoachPage from "@/app/coach/page";
import { BROWSER_MODEL, resetBrowserModelForTests } from "@/lib/ai/browser-model";
import { resetCoach } from "@/lib/ai/coach-chat";
import { getAiSettings } from "@/lib/ai/settings";
import type { CalorieProfile } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos del modelo en el navegador (D9, fase 10):
 *  - sin WebGPU lo dice en una línea y no ofrece descargar nada,
 *  - con WebGPU descarga el modelo (con progreso) y deja elegir «Nube / Este navegador»
 *    para el coach,
 *  - el coach responde con ese modelo sin llamar a ningún proveedor,
 *  - «Eliminar del navegador» borra el modelo y el coach vuelve a la nube.
 * WebLLM y WebGPU están simulados: happy-dom no tiene GPU.
 */

const engine = {
  chat: {
    completions: {
      create: vi.fn(async () =>
        (async function* () {
          yield { choices: [{ delta: { content: "Una ensalada " } }] };
          yield { choices: [{ delta: { content: "con pollo." } }] };
        })(),
      ),
    },
  },
  interruptGenerate: vi.fn(),
  unload: vi.fn(async () => undefined),
};

vi.mock("@mlc-ai/web-llm", () => ({
  hasModelInCache: vi.fn(async () => false),
  CreateMLCEngine: vi.fn(async (_id: string, options: { initProgressCallback?: (r: { progress: number }) => void }) => {
    options.initProgressCallback?.({ progress: 0.5 });
    options.initProgressCallback?.({ progress: 1 });
    return engine;
  }),
  deleteModelAllInfoInCache: vi.fn(async () => undefined),
}));

const profile: CalorieProfile = {
  gender: null,
  birthYear: null,
  heightCm: null,
  gymDaysPerWeek: null,
  gymSessionMinutes: null,
  walkingMinutesPerDay: null,
  calorieGoal: null,
};

vi.mock("@/lib/api", () => ({
  ApiError: class ApiError extends Error {},
  api: {
    session: vi.fn(async () => ({ username: "ana", isAdmin: false, calorieProfile: profile })),
    listMeals: vi.fn(async () => []),
    listWeights: vi.fn(async () => []),
  },
}));

import * as webllm from "@mlc-ai/web-llm";

const fetchMock = vi.fn<typeof fetch>();

function withWebGpu(f16 = true) {
  vi.stubGlobal("navigator", {
    ...navigator,
    gpu: { requestAdapter: async () => ({ features: { has: (name: string) => f16 && name === "shader-f16" } }) },
  });
}

describe("modelo en el navegador", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    resetCoach();
    resetBrowserModelForTests();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sin WebGPU lo dice y no ofrece descargar", async () => {
    render(<AiSettingsCard />);
    expect(await screen.findByText(t.ai.browserUnsupported)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: new RegExp(BROWSER_MODEL.name) })).toBeNull();
    expect(vi.mocked(webllm.CreateMLCEngine)).not.toHaveBeenCalled();
  });

  it("descarga el modelo y deja usarlo para el coach", async () => {
    withWebGpu();
    const user = userEvent.setup();
    render(<AiSettingsCard />);

    const download = await screen.findByRole("button", {
      name: formatTemplate(t.ai.browserDownload, { model: BROWSER_MODEL.name, size: "1" }),
    });
    await user.click(download);
    expect(vi.mocked(webllm.CreateMLCEngine)).toHaveBeenCalledWith(BROWSER_MODEL.f16, expect.anything());
    expect(await screen.findByText(formatTemplate(t.ai.browserReady, { model: BROWSER_MODEL.name }))).toBeInTheDocument();

    const choice = screen.getByRole("group", { name: t.ai.useForCoach });
    await user.click(within(choice).getByRole("button", { name: t.ai.engineBrowser }));
    expect(getAiSettings().coachEngine).toBe("browser");

    await user.click(screen.getByRole("button", { name: t.ai.browserDelete }));
    await waitFor(() => expect(vi.mocked(webllm.deleteModelAllInfoInCache)).toHaveBeenCalledWith(BROWSER_MODEL.f16));
    expect(getAiSettings().coachEngine).toBe("cloud");
  });

  it("sin «shader-f16» usa la versión f32 del modelo", async () => {
    withWebGpu(false);
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    await user.click(await screen.findByRole("button", { name: new RegExp(BROWSER_MODEL.name) }));
    expect(vi.mocked(webllm.CreateMLCEngine)).toHaveBeenCalledWith(BROWSER_MODEL.f32, expect.anything());
  });

  it("el coach responde con el modelo del navegador, sin llamar a ningún proveedor", async () => {
    withWebGpu();
    resetBrowserModelForTests({ status: "ready" });
    localStorage.setItem("bw:ai", JSON.stringify({ coachEngine: "browser", coachSeesData: false }));
    const user = userEvent.setup();
    render(<CoachPage />);

    expect(screen.getByText(new RegExp(formatTemplate(t.coach.engineBrowser, { model: BROWSER_MODEL.name })))).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: t.coach.examples[3] }));
    expect(await screen.findByText("Una ensalada con pollo.")).toBeInTheDocument();

    expect(fetchMock).not.toHaveBeenCalled();
    expect(engine.chat.completions.create).toHaveBeenCalledWith(
      expect.objectContaining({
        stream: true,
        extra_body: { enable_thinking: false },
        messages: [
          expect.objectContaining({ role: "system" }),
          { role: "user", content: t.coach.examples[3] },
        ],
      }),
    );
  });
});
