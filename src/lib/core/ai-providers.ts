/**
 * Cloud AI providers: how to build a request and read the answer for each API
 * family. Pure (no I/O): web (`fetch`) and Android (OkHttp) only do the
 * transport, so both platforms send exactly the same requests.
 *
 * Families: Google Gemini, OpenAI-compatible chat completions (OpenAI,
 * OpenRouter, Ollama / LM Studio / any compatible server) and Anthropic.
 */

export type AiProvider = "gemini" | "openai" | "anthropic" | "openrouter" | "custom";

export interface AiConfig {
  provider: AiProvider;
  apiKey: string;
  model: string;
  /** Only for `custom`: the server's OpenAI-compatible base URL (…/v1). */
  baseUrl: string;
}

export interface AiImage {
  mimeType: string;
  /** Base64 without the `data:` prefix. */
  data: string;
}

export interface AiMessage {
  role: "user" | "assistant";
  text: string;
  /** Only on user messages (photos of a meal). */
  images?: AiImage[];
}

export interface AiInput {
  system: string;
  messages: AiMessage[];
  /** Ask for a JSON object (meal estimates). */
  json: boolean;
  stream: boolean;
  maxTokens: number;
}

export interface AiHttpRequest {
  url: string;
  headers: Record<string, string>;
  body: string;
}

export const DEFAULT_MODELS: Record<AiProvider, string> = {
  gemini: "gemini-flash-latest",
  openai: "gpt-5-mini",
  anthropic: "claude-haiku-4-5",
  openrouter: "openrouter/auto",
  custom: "",
};

const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
const OPENAI_BASE = "https://api.openai.com/v1";
const OPENROUTER_BASE = "https://openrouter.ai/api/v1";
const ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";

function openAiBase(config: AiConfig): string {
  if (config.provider === "openai") return OPENAI_BASE;
  if (config.provider === "openrouter") return OPENROUTER_BASE;
  return config.baseUrl.trim().replace(/\/+$/, "");
}

/** The HTTP request for one call. `stream` asks for server-sent events. */
export function buildAiRequest(config: AiConfig, input: AiInput): AiHttpRequest {
  const model = config.model.trim() || DEFAULT_MODELS[config.provider];

  if (config.provider === "gemini") {
    const method = input.stream ? "streamGenerateContent?alt=sse" : "generateContent";
    const generationConfig: Record<string, unknown> = { maxOutputTokens: input.maxTokens };
    if (input.json) generationConfig.responseMimeType = "application/json";
    return {
      url: `${GEMINI_BASE}${encodeURIComponent(model)}:${method}`,
      headers: { "Content-Type": "application/json", "x-goog-api-key": config.apiKey },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: input.system }] },
        contents: input.messages.map((message) => ({
          role: message.role === "assistant" ? "model" : "user",
          parts: [
            ...(message.images ?? []).map((image) => ({ inline_data: { mime_type: image.mimeType, data: image.data } })),
            { text: message.text },
          ],
        })),
        generationConfig,
      }),
    };
  }

  if (config.provider === "anthropic") {
    return {
      url: ANTHROPIC_URL,
      headers: {
        "Content-Type": "application/json",
        "x-api-key": config.apiKey,
        "anthropic-version": "2023-06-01",
        // The key never leaves the user's device except to Anthropic.
        "anthropic-dangerous-direct-browser-access": "true",
      },
      body: JSON.stringify({
        model,
        max_tokens: input.maxTokens,
        system: input.json ? `${input.system}\nReply with JSON only.` : input.system,
        messages: input.messages.map((message) => ({
          role: message.role,
          content: [
            ...(message.images ?? []).map((image) => ({
              type: "image",
              source: { type: "base64", media_type: image.mimeType, data: image.data },
            })),
            { type: "text", text: message.text },
          ],
        })),
        stream: input.stream,
      }),
    };
  }

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (config.apiKey.trim()) headers.Authorization = `Bearer ${config.apiKey.trim()}`;
  const body: Record<string, unknown> = {
    model,
    messages: [
      { role: "system", content: input.system },
      ...input.messages.map((message) => ({
        role: message.role,
        content:
          message.images && message.images.length > 0
            ? [
                ...message.images.map((image) => ({
                  type: "image_url",
                  image_url: { url: `data:${image.mimeType};base64,${image.data}` },
                })),
                { type: "text", text: message.text },
              ]
            : message.text,
      })),
    ],
    stream: input.stream,
  };
  // OpenAI's reasoning models only accept the new name; other servers know the old one.
  body[config.provider === "openai" ? "max_completion_tokens" : "max_tokens"] = input.maxTokens;
  if (input.json) body.response_format = { type: "json_object" };
  return { url: `${openAiBase(config)}/chat/completions`, headers, body: JSON.stringify(body) };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function geminiText(json: unknown): string {
  const candidates = asRecord(json)?.candidates;
  const parts = Array.isArray(candidates) ? asRecord(asRecord(candidates[0])?.content)?.parts : null;
  if (!Array.isArray(parts)) return "";
  return parts.map((part) => asRecord(part)?.text).filter((text): text is string => typeof text === "string").join("");
}

/** The answer text of a non-streaming response. */
export function parseAiResponse(provider: AiProvider, json: unknown): string {
  if (provider === "gemini") return geminiText(json);
  if (provider === "anthropic") {
    const content = asRecord(json)?.content;
    if (!Array.isArray(content)) return "";
    return content
      .map((block) => asRecord(block))
      .filter((block) => block?.type === "text" && typeof block.text === "string")
      .map((block) => block!.text as string)
      .join("");
  }
  const choices = asRecord(json)?.choices;
  const message = Array.isArray(choices) ? asRecord(asRecord(choices[0])?.message) : null;
  return typeof message?.content === "string" ? message.content : "";
}

/**
 * The text a server-sent-events line adds to a streamed answer («data: {…}»),
 * or null when the line carries none (comments, pings, `[DONE]`, other events).
 */
export function parseAiStreamLine(provider: AiProvider, line: string): string | null {
  if (!line.startsWith("data:")) return null;
  const data = line.slice(5).trim();
  if (data === "" || data === "[DONE]") return null;
  let json: unknown;
  try {
    json = JSON.parse(data);
  } catch {
    return null;
  }
  if (provider === "gemini") return geminiText(json) || null;
  if (provider === "anthropic") {
    const event = asRecord(json);
    const delta = asRecord(event?.delta);
    return event?.type === "content_block_delta" && typeof delta?.text === "string" ? delta.text : null;
  }
  const choices = asRecord(json)?.choices;
  const delta = Array.isArray(choices) ? asRecord(asRecord(choices[0])?.delta) : null;
  return typeof delta?.content === "string" && delta.content !== "" ? delta.content : null;
}

/** Enough to make a call: a key (a custom server may not need one), a server and a model. */
export function isAiConfigured(config: AiConfig): boolean {
  if (config.provider === "custom") return config.baseUrl.trim() !== "" && config.model.trim() !== "";
  return config.apiKey.trim() !== "";
}

export type AiErrorKind = "invalid_key" | "quota" | "not_found" | "unavailable" | "provider";

/**
 * What went wrong, for a message the user understands. Gemini answers a wrong
 * key with 400 + `API_KEY_INVALID`, hence the look at the body.
 */
export function aiErrorKind(status: number, body: string): AiErrorKind {
  if (status === 401 || status === 403) return "invalid_key";
  if (status === 400 && /api[_ -]?key/i.test(body)) return "invalid_key";
  if (status === 402 || status === 429) return "quota";
  if (status === 404) return "not_found";
  // Overloaded or down for a moment (Gemini often answers 503): worth retrying.
  if (status === 500 || status === 502 || status === 503 || status === 504) return "unavailable";
  return "provider";
}

/**
 * The provider's own error text (`{"error": {"message": …}}` for Gemini, OpenAI,
 * OpenRouter and Anthropic), trimmed, so the user can see what went wrong.
 */
export function aiErrorDetail(body: string): string {
  let message: unknown = null;
  try {
    const json = asRecord(JSON.parse(body));
    const error = json?.error;
    message = typeof error === "string" ? error : asRecord(error)?.message ?? json?.message;
  } catch {
    message = null;
  }
  const text = typeof message === "string" ? message : body;
  return text.replace(/\s+/g, " ").trim().slice(0, 200);
}

/**
 * How a streamed answer ended: `done` (the model finished), `length` (it hit
 * the token limit, mid-sentence) or `error` (the provider reported a failure
 * inside the stream). A stream that ends without any of these was cut off.
 */
export type AiStreamEnd =
  | { kind: "done" }
  | { kind: "length" }
  | { kind: "error"; errorKind: AiErrorKind; detail: string };

const ANTHROPIC_ERROR_KINDS: Record<string, AiErrorKind> = {
  overloaded_error: "unavailable",
  api_error: "unavailable",
  rate_limit_error: "quota",
  authentication_error: "invalid_key",
  permission_error: "invalid_key",
  not_found_error: "not_found",
};

function streamError(errorKind: AiErrorKind, detail: string): AiStreamEnd {
  return { kind: "error", errorKind, detail: detail.replace(/\s+/g, " ").trim().slice(0, 200) };
}

/**
 * Whether a server-sent-events line ends the streamed answer, and how; null
 * when it does not (text, pings, other events). Read after [parseAiStreamLine]:
 * Gemini's last chunk carries text and its finish reason together.
 */
export function parseAiStreamEnd(provider: AiProvider, line: string): AiStreamEnd | null {
  if (!line.startsWith("data:")) return null;
  const data = line.slice(5).trim();
  if (data === "") return null;
  if (data === "[DONE]") return { kind: "done" };
  let json: Record<string, unknown> | null;
  try {
    json = asRecord(JSON.parse(data));
  } catch {
    return null;
  }
  if (!json) return null;

  if (provider === "anthropic") {
    if (json.type === "message_stop") return { kind: "done" };
    if (json.type === "error") {
      const error = asRecord(json.error);
      const type = typeof error?.type === "string" ? error.type : "";
      return streamError(ANTHROPIC_ERROR_KINDS[type] ?? "provider", aiErrorDetail(data));
    }
    const stop = json.type === "message_delta" ? asRecord(json.delta)?.stop_reason : null;
    if (stop === "max_tokens") return { kind: "length" };
    if (stop === "refusal") return streamError("provider", "stop_reason: refusal");
    return null;
  }

  // Gemini, OpenAI and OpenRouter put a failure in the stream as {"error": {"code": …, "message": …}}.
  if (json.error !== undefined && json.error !== null) {
    const code = asRecord(json.error)?.code;
    return streamError(typeof code === "number" ? aiErrorKind(code, data) : "provider", aiErrorDetail(data));
  }

  if (provider === "gemini") {
    const blocked = asRecord(json.promptFeedback)?.blockReason;
    if (typeof blocked === "string") return streamError("provider", `blockReason: ${blocked}`);
    const candidates = json.candidates;
    const reason = Array.isArray(candidates) ? asRecord(candidates[0])?.finishReason : null;
    if (typeof reason !== "string" || reason === "") return null;
    if (reason === "STOP") return { kind: "done" };
    if (reason === "MAX_TOKENS") return { kind: "length" };
    return streamError("provider", `finishReason: ${reason}`);
  }

  const choices = json.choices;
  const reason = Array.isArray(choices) ? asRecord(choices[0])?.finish_reason : null;
  if (typeof reason !== "string" || reason === "") return null;
  if (reason === "length") return { kind: "length" };
  if (reason === "content_filter" || reason === "error") return streamError("provider", `finish_reason: ${reason}`);
  return { kind: "done" };
}
