import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AiSettingsCard } from "@/components/settings/ai-settings-card";
import { AiError, aiComplete, aiStream, RETRY_DELAYS_MS } from "@/lib/ai/client";
import { getAiSettings } from "@/lib/ai/settings";
import type { AiConfig, AiInput } from "@/lib/core/ai-providers";
import { formatTemplate, t } from "@/i18n";

/**
 * Requisitos de Ajustes → IA y del cliente de IA del navegador:
 *  - la clave se guarda solo en este navegador (localStorage), una por proveedor,
 *  - «Probar» llama directamente al proveedor elegido (nunca a nuestro servidor)
 *    y explica los errores (clave no válida, límite, sin conexión),
 *  - la guía de la clave gratis de Google enlaza a AI Studio,
 *  - el modelo se elige de la lista que da el proveedor para tu clave (guardada en
 *    este navegador), con «Predeterminado» y «Otro…» para escribir un nombre,
 *  - «El coach puede ver mis datos» se puede desactivar,
 *  - las respuestas en streaming llegan por trozos aunque se corten a mitad de línea.
 * Nunca se llama a un proveedor real: `fetch` está simulado.
 */

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  localStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Ajustes → IA", () => {
  it("guarda la clave de Gemini en este navegador y «Probar» llama a Google directamente", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: "OK" }] } }] })),
    );
    const user = userEvent.setup();
    render(<AiSettingsCard />);

    expect(screen.getByLabelText(t.ai.provider)).toHaveValue("gemini");
    expect(screen.getByText(t.ai.freeKeyOpen).closest("a")).toHaveAttribute(
      "href",
      "https://aistudio.google.com/api-keys",
    );
    await user.type(screen.getByLabelText(t.ai.apiKey), "fake-gemini-key");
    expect(getAiSettings().apiKeys.gemini).toBe("fake-gemini-key");
    expect(fetchMock).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: t.ai.test }));
    expect(await screen.findByText(t.ai.testOk)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/^https:\/\/generativelanguage\.googleapis\.com\/v1beta\/models\/gemini-flash-latest:/);
    expect((init?.headers as Record<string, string>)["x-goog-api-key"]).toBe("fake-gemini-key");
  });

  it("con la clave, el modelo se elige de la lista del proveedor; «Otro…» deja escribirlo", async () => {
    fetchMock.mockImplementation(async (input) =>
      String(input).includes("/models?")
        ? new Response(
            JSON.stringify({
              models: [
                { name: "models/gemini-3-flash", displayName: "Gemini 3 Flash", supportedGenerationMethods: ["generateContent"] },
                { name: "models/gemini-2.5-flash-lite", displayName: "Gemini 2.5 Flash-Lite", supportedGenerationMethods: ["generateContent"] },
                { name: "models/gemini-2.5-flash-image", displayName: "Nano Banana", supportedGenerationMethods: ["generateContent"] },
              ],
            }),
          )
        : new Response("{}", { status: 500 }),
    );
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    // Without a key there is nothing to list: the plain field.
    expect(screen.getByText(t.ai.modelListNeedsKey)).toBeInTheDocument();

    await user.type(screen.getByLabelText(t.ai.apiKey), "fake-gemini-key");
    const select = await screen.findByRole("combobox", { name: t.ai.model }, { timeout: 3000 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000");
    expect((init?.headers as Record<string, string>)["x-goog-api-key"]).toBe("fake-gemini-key");
    expect([...(select as HTMLSelectElement).options].map((option) => option.textContent)).toEqual([
      formatTemplate(t.ai.modelDefault, { model: "gemini-flash-latest" }),
      "Gemini 3 Flash",
      "Gemini 2.5 Flash-Lite",
      t.ai.modelOther,
    ]);

    await user.selectOptions(select, "gemini-2.5-flash-lite");
    expect(getAiSettings().models.gemini).toBe("gemini-2.5-flash-lite");

    await user.selectOptions(select, t.ai.modelOther);
    await user.type(screen.getByRole("textbox", { name: t.ai.model }), "x");
    expect(getAiSettings().models.gemini).toBe("gemini-2.5-flash-litex");
  });

  it("explica una clave no válida", async () => {
    fetchMock.mockResolvedValue(
      new Response('{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}', {
        status: 400,
      }),
    );
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    await user.type(screen.getByLabelText(t.ai.apiKey), "wrong");
    await user.click(screen.getByRole("button", { name: t.ai.test }));
    expect(await screen.findByText(t.ai.errors.invalid_key)).toBeInTheDocument();
  });

  it("sin clave, «Probar» pide configurarla sin llamar a nadie", async () => {
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    await user.click(screen.getByRole("button", { name: t.ai.test }));
    expect(await screen.findByText(t.ai.errors.not_configured)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("guarda una clave por proveedor y pide la dirección para un servidor propio", async () => {
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    await user.type(screen.getByLabelText(t.ai.apiKey), "gemini-key");

    await user.selectOptions(screen.getByLabelText(t.ai.provider), "anthropic");
    expect(screen.getByLabelText(t.ai.apiKey)).toHaveValue("");
    expect(screen.queryByText(t.ai.freeKeyTitle)).toBeNull();
    await user.type(screen.getByLabelText(t.ai.apiKey), "claude-key");

    await user.selectOptions(screen.getByLabelText(t.ai.provider), "custom");
    await user.type(screen.getByLabelText(t.ai.baseUrl), "http://localhost:11434/v1");
    await user.type(screen.getByLabelText(t.ai.model), "gemma3:4b");
    expect(screen.getByText(t.ai.localHint)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(t.ai.provider), "gemini");
    expect(screen.getByLabelText(t.ai.apiKey)).toHaveValue("gemini-key");
    expect(getAiSettings()).toMatchObject({
      provider: "gemini",
      apiKeys: { gemini: "gemini-key", anthropic: "claude-key" },
      models: { custom: "gemma3:4b" },
      baseUrl: "http://localhost:11434/v1",
    });
  });

  it("«El coach puede ver mis datos» está activado y se puede desactivar", async () => {
    const user = userEvent.setup();
    render(<AiSettingsCard />);
    const toggle = screen.getByRole("checkbox", { name: new RegExp(t.ai.coachSeesData) });
    expect(toggle).toBeChecked();
    await user.click(toggle);
    expect(getAiSettings().coachSeesData).toBe(false);
  });
});

describe("cliente de IA", () => {
  const openai: AiConfig = { provider: "openai", apiKey: "k", model: "", baseUrl: "" };
  const input: AiInput = { system: "s", messages: [{ role: "user", text: "hola" }], json: false, stream: true, maxTokens: 100 };

  function sse(chunks: string[]): Response {
    const encoder = new TextEncoder();
    return new Response(
      new ReadableStream({
        start(controller) {
          chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
          controller.close();
        },
      }),
      { headers: { "Content-Type": "text/event-stream" } },
    );
  }

  it("junta los trozos del streaming aunque una línea llegue partida", async () => {
    fetchMock.mockResolvedValue(
      sse([
        'data: {"choices":[{"delta":{"content":"Ho"}}]}\n\ndata: {"choices":[{"de',
        'lta":{"content":"la"}}]}\n\n',
        "data: [DONE]\n\n",
      ]),
    );
    const pieces: string[] = [];
    for await (const piece of aiStream(openai, input)) pieces.push(piece);
    expect(pieces).toEqual(["Ho", "la"]);
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body)).stream).toBe(true);
  });

  it("una respuesta que se para antes de terminar falla después del texto recibido", async () => {
    const read = async (response: Response, config: AiConfig = openai) => {
      fetchMock.mockResolvedValueOnce(response);
      const pieces: string[] = [];
      const error = await (async () => {
        for await (const piece of aiStream(config, input)) pieces.push(piece);
      })().catch((e: unknown) => e);
      return { pieces, error: error as AiError | undefined };
    };

    // Token limit (reasoning models spend part of it thinking).
    let result = await read(sse(['data: {"choices":[{"delta":{"content":"Lunes"}}]}\n\n', 'data: {"choices":[{"delta":{},"finish_reason":"length"}]}\n\n']));
    expect(result.pieces).toEqual(["Lunes"]);
    expect(result.error).toMatchObject({ kind: "truncated" });

    // The connection ends without [DONE] or a finish reason.
    result = await read(sse(['data: {"choices":[{"delta":{"content":"Lunes"}}]}\n\n']));
    expect(result.pieces).toEqual(["Lunes"]);
    expect(result.error).toMatchObject({ kind: "interrupted" });

    // The provider reports a failure inside the stream (Anthropic, overloaded).
    const anthropic: AiConfig = { provider: "anthropic", apiKey: "k", model: "", baseUrl: "" };
    result = await read(
      sse([
        'event: content_block_delta\ndata: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Lunes"}}\n\n',
        'event: error\ndata: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}\n\n',
      ]),
      anthropic,
    );
    expect(result.pieces).toEqual(["Lunes"]);
    expect(result.error).toMatchObject({ kind: "unavailable", detail: "Overloaded" });

    // Gemini's last chunk: text and finish reason together.
    const gemini: AiConfig = { provider: "gemini", apiKey: "k", model: "", baseUrl: "" };
    result = await read(sse(['data: {"candidates":[{"content":{"parts":[{"text":"Fin."}]},"finishReason":"STOP"}]}\n\n']), gemini);
    expect(result.pieces).toEqual(["Fin."]);
    expect(result.error).toBeUndefined();
  });

  it("reintenta si el proveedor está saturado y luego explica el error con sus palabras", async () => {
    RETRY_DELAYS_MS.splice(0, RETRY_DELAYS_MS.length, 0, 0);
    const overloaded = '{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}';
    fetchMock.mockResolvedValueOnce(new Response(overloaded, { status: 503 }));
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ choices: [{ message: { content: "Hola" } }] })));
    await expect(aiComplete(openai, input)).resolves.toBe("Hola");

    fetchMock.mockImplementation(async () => new Response(overloaded, { status: 503 }));
    const error = await aiComplete(openai, input).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(AiError);
    expect((error as AiError).kind).toBe("unavailable");
    expect((error as AiError).detail).toBe("503: The model is overloaded. Please try again later.");
    expect(fetchMock).toHaveBeenCalledTimes(5);
  });

  it("distingue sin conexión, límite y respuesta vacía", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await expect(aiComplete(openai, input)).rejects.toMatchObject({ kind: "offline" });

    fetchMock.mockResolvedValueOnce(new Response("{}", { status: 429 }));
    await expect(aiComplete(openai, input)).rejects.toMatchObject({ kind: "quota" });

    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ choices: [{ message: { content: " " } }] })));
    const error = await aiComplete(openai, input).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(AiError);
    expect((error as AiError).kind).toBe("empty");
    expect(JSON.parse(String(fetchMock.mock.calls[2][1]?.body)).stream).toBe(false);
  });
});
