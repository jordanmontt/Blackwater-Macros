import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AiSettingsCard } from "@/components/settings/ai-settings-card";
import CoachPage from "@/app/coach/page";
import { BROWSER_MODELS, DEFAULT_BROWSER_MODEL as BROWSER_MODEL, resetBrowserModelForTests, withoutThinking } from "@/lib/ai/browser-model";
import { resetCoach } from "@/lib/ai/coach-chat";
import { getAiSettings } from "@/lib/ai/settings";
import type { CalorieProfile } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos del modelo en el navegador:
 *  - sin WebGPU (o con WebGPU demasiado limitado, como Firefox) lo dice en una línea y no
 *    ofrece descargar nada,
 *  - se elige el modelo de una lista (como en Android) antes de descargarlo; uno ya
 *    descargado se reconoce, también el modelo anterior,
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

function withWebGpu(f16 = true, storageBuffers = 10) {
  vi.stubGlobal("navigator", {
    ...navigator,
    gpu: {
      requestAdapter: async () => ({
        features: { has: (name: string) => f16 && name === "shader-f16" },
        limits: { maxStorageBuffersPerShaderStage: storageBuffers },
      }),
    },
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
      name: formatTemplate(t.ai.browserDownload, { model: BROWSER_MODEL.name, size: "1,1" }),
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

  it("con WebGPU demasiado limitado (Firefox) lo explica en vez de fallar al descargar", async () => {
    withWebGpu(true, 9);
    render(<AiSettingsCard />);
    expect(await screen.findByText(t.ai.browserLimited)).toBeInTheDocument();
    expect(vi.mocked(webllm.CreateMLCEngine)).not.toHaveBeenCalled();
  });

  it("se elige otro modelo de la lista antes de descargarlo y se recuerda", async () => {
    withWebGpu();
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    const light = BROWSER_MODELS.find((model) => model.id === "qwen3.5-4b")!;
    const section = await screen.findByRole("region", { name: t.ai.browserTitle });
    await user.selectOptions(await within(section).findByLabelText(t.ai.browserModel), light.id);
    expect(getAiSettings().browserModel).toBe(light.id);
    expect(screen.getByText(t.ai.browserModelNotes["qwen3.5-4b"])).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: new RegExp(light.name) }));
    expect(vi.mocked(webllm.CreateMLCEngine)).toHaveBeenCalledWith(light.f16, expect.anything());
  });

  it("reconoce el modelo anterior ya descargado (y deja borrarlo)", async () => {
    withWebGpu();
    vi.mocked(webllm.hasModelInCache).mockImplementation(async (id: string) => id === "Qwen3-1.7B-q4f16_1-MLC");
    render(<AiSettingsCard />);
    expect(await screen.findByText(formatTemplate(t.ai.browserReady, { model: "Qwen3 1.7B" }))).toBeInTheDocument();
    expect(screen.getByRole("button", { name: t.ai.browserDelete })).toBeInTheDocument();
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
    resetBrowserModelForTests({ status: "ready", model: BROWSER_MODEL });
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

describe("withoutThinking", () => {
  it("quita el bloque <think> vacío que Qwen escribe aunque no piense", () => {
    const visible = withoutThinking();
    expect(["<thi", "nk>\n\n</th", "ink>\n\nCal", "orías: 336"].map(visible).join("")).toBe("Calorías: 336");
  });

  it("deja pasar una respuesta que no empieza con <think>", () => {
    const visible = withoutThinking();
    expect(["Cal", "orías"].map(visible).join("")).toBe("Calorías");
  });
});
