import {
  aiErrorDetail,
  aiErrorKind,
  buildAiRequest,
  isAiConfigured,
  parseAiResponse,
  parseAiStreamEnd,
  parseAiStreamLine,
  type AiConfig,
  type AiErrorKind,
  type AiInput,
} from "@/lib/core/ai-providers";

/**
 * The browser talks to the AI provider directly (no proxy through our server):
 * the key and the photos only travel to the provider the user chose.
 */

export type AiFailure = AiErrorKind | "not_configured" | "offline" | "empty" | "unreadable" | "truncated" | "interrupted";

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

/** The lines of a streamed response as they arrive (a line split across chunks is joined). */
async function* streamLines(response: Response, signal?: AbortSignal): AsyncGenerator<string> {
  if (!response.body) {
    yield* (await response.text()).split(/\r?\n/);
    return;
  }
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";
  try {
    while (true) {
      let chunk: ReadableStreamReadResult<string>;
      try {
        chunk = await reader.read();
      } catch (error) {
        if (signal?.aborted) throw error;
        throw new AiError("offline", error instanceof Error ? error.message : "");
      }
      if (chunk.done) break;
      buffer += chunk.value;
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop() ?? "";
      yield* lines;
    }
    yield buffer;
  } finally {
    reader.releaseLock();
  }
}

/**
 * The answer piece by piece as it is written (Coach). An answer that stops early
 * fails after the text that did arrive: `truncated` at the token limit, the
 * provider's error when it reports one, `interrupted` when the stream just ends
 * without saying the answer is done.
 */
export async function* aiStream(config: AiConfig, input: AiInput, signal?: AbortSignal): AsyncGenerator<string> {
  const response = await send(config, { ...input, stream: true }, signal);
  let finished = false;
  for await (const line of streamLines(response, signal)) {
    const delta = parseAiStreamLine(config.provider, line);
    if (delta) yield delta;
    const end = parseAiStreamEnd(config.provider, line);
    if (end?.kind === "done") finished = true;
    else if (end?.kind === "length") throw new AiError("truncated");
    else if (end?.kind === "error") throw new AiError(end.errorKind, end.detail);
  }
  if (!finished) throw new AiError("interrupted");
}

/** «Probar»: a tiny call; a 2xx answer means key, model and server are right. */
export async function testAi(config: AiConfig, signal?: AbortSignal): Promise<void> {
  await send(
    config,
    { system: "Reply with the word OK.", messages: [{ role: "user", text: "OK?" }], json: false, stream: false, maxTokens: 64 },
    signal,
  );
}
