import {
  aiErrorDetail,
  aiErrorKind,
  buildAiRequest,
  isAiConfigured,
  parseAiResponse,
  parseAiStreamLine,
  type AiConfig,
  type AiErrorKind,
  type AiInput,
} from "@/lib/core/ai-providers";

/**
 * The browser talks to the AI provider directly (no proxy through our server):
 * the key and the photos only travel to the provider the user chose.
 */

export type AiFailure = AiErrorKind | "not_configured" | "offline" | "empty" | "unreadable";

export class AiError extends Error {
  readonly kind: AiFailure;
  /** The provider's (or engine's) own words, shown small under the message. */
  readonly detail: string;
  constructor(kind: AiFailure, detail = "") {
    super(detail || kind);
    this.kind = kind;
    this.detail = detail;
  }
}

/** Waits before retrying an overloaded provider (Gemini answers 503 now and then). */
export const RETRY_DELAYS_MS = [1500, 4000];

function wait(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener("abort", () => {
      clearTimeout(timer);
      resolve();
    });
  });
}

async function send(config: AiConfig, input: AiInput, signal?: AbortSignal): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await sendOnce(config, input, signal);
    } catch (error) {
      const retry = error instanceof AiError && error.kind === "unavailable" && attempt < RETRY_DELAYS_MS.length;
      if (!retry || signal?.aborted) throw error;
      await wait(RETRY_DELAYS_MS[attempt], signal);
    }
  }
}

async function sendOnce(config: AiConfig, input: AiInput, signal?: AbortSignal): Promise<Response> {
  if (!isAiConfigured(config)) throw new AiError("not_configured");
  const request = buildAiRequest(config, input);
  let response: Response;
  try {
    response = await fetch(request.url, { method: "POST", headers: request.headers, body: request.body, signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new AiError("offline", error instanceof Error ? error.message : "");
  }
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new AiError(aiErrorKind(response.status, body), `${response.status}: ${aiErrorDetail(body)}`);
  }
  return response;
}

/** One answer, whole (meal estimates). */
export async function aiComplete(config: AiConfig, input: AiInput, signal?: AbortSignal): Promise<string> {
  const response = await send(config, { ...input, stream: false }, signal);
  const text = parseAiResponse(config.provider, await response.json().catch(() => null));
  if (!text.trim()) throw new AiError("empty");
  return text;
}

/** The answer piece by piece as it is written (Coach). */
export async function* aiStream(config: AiConfig, input: AiInput, signal?: AbortSignal): AsyncGenerator<string> {
  const response = await send(config, { ...input, stream: true }, signal);
  if (!response.body) {
    const text = await response.text();
    for (const line of text.split(/\r?\n/)) {
      const delta = parseAiStreamLine(config.provider, line);
      if (delta) yield delta;
    }
    return;
  }
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        const delta = parseAiStreamLine(config.provider, line);
        if (delta) yield delta;
      }
    }
    const delta = parseAiStreamLine(config.provider, buffer);
    if (delta) yield delta;
  } finally {
    reader.releaseLock();
  }
}

/** «Probar»: a tiny call; a 2xx answer means key, model and server are right. */
export async function testAi(config: AiConfig, signal?: AbortSignal): Promise<void> {
  await send(
    config,
    { system: "Reply with the word OK.", messages: [{ role: "user", text: "OK?" }], json: false, stream: false, maxTokens: 64 },
    signal,
  );
}
