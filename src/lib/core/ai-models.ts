import type { AiConfig, AiProvider } from "./ai-providers";

/**
 * The models a key can use, asked to the provider itself (nothing hard-coded):
 * the model dropdown in Ajustes → IA stays current when providers add models.
 * Pure: the request and the reading of the answer; web and Android do the GET.
 */

export interface AiModelOption {
  /** What goes in the request (`gemini-2.5-flash`). */
  id: string;
  /** What the user reads (`Gemini 2.5 Flash`). */
  label: string;
}

export interface AiModelListRequest {
  url: string;
  headers: Record<string, string>;
}

const GEMINI_MODELS = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000";
const ANTHROPIC_MODELS = "https://api.anthropic.com/v1/models?limit=1000";

function openAiModelsUrl(config: AiConfig): string {
  if (config.provider === "openai") return "https://api.openai.com/v1/models";
  if (config.provider === "openrouter") return "https://openrouter.ai/api/v1/models";
  return `${config.baseUrl.trim().replace(/\/+$/, "")}/models`;
}

/** The GET that lists the models for [config]'s key. */
export function buildModelListRequest(config: AiConfig): AiModelListRequest {
  const key = config.apiKey.trim();
  if (config.provider === "gemini") return { url: GEMINI_MODELS, headers: { "x-goog-api-key": key } };
  if (config.provider === "anthropic") {
    return {
      url: ANTHROPIC_MODELS,
      headers: { "x-api-key": key, "anthropic-version": "2023-06-01", "anthropic-dangerous-direct-browser-access": "true" },
    };
  }
  return { url: openAiModelsUrl(config), headers: key ? { Authorization: `Bearer ${key}` } : {} };
}

/**
 * Gemini also lists models that cannot chat: embeddings, image, music and
 * speech generation, live audio, agents (Nano Banana, Lyria, Antigravity…).
 * Only `gemini-…` ids are chat models, and not all of them. Gemma through the
 * API has no system instruction, which the app needs.
 */
const GEMINI_CHAT = /^gemini-/;
const GEMINI_EXCLUDED = /(embedding|aqa|imagen|veo|image|tts|live|native-audio|computer-use|robotics|deep-research|gemma|learnlm)/;
const OPENAI_CHAT = /^(gpt-|o\d|chatgpt-)/;
const OPENAI_EXCLUDED = /(audio|realtime|transcribe|tts|image|search|embedding|instruct|moderation|codex|computer-use|dall-e|whisper)/;

type Json = Record<string, unknown>;

function records(value: unknown): Json[] {
  return Array.isArray(value) ? value.filter((item): item is Json => typeof item === "object" && item !== null) : [];
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * Numbers compared as numbers, the rest by character code: «gemini-3.8» after
 * «gemini-3.1», «gpt-5.10» after «gpt-5.9». Written out (not `localeCompare`) so
 * the Kotlin mirror orders exactly the same.
 */
export function naturalCompare(a: string, b: string): number {
  const x = a.toLowerCase().match(/\d+|\D+/g) ?? [];
  const y = b.toLowerCase().match(/\d+|\D+/g) ?? [];
  for (let i = 0; i < Math.min(x.length, y.length); i++) {
    const [p, q] = [x[i], y[i]];
    if (/^\d/.test(p) && /^\d/.test(q)) {
      const byLength = p.replace(/^0+/, "").length - q.replace(/^0+/, "").length;
      if (byLength !== 0) return Math.sign(byLength);
      const byDigits = p.replace(/^0+/, "") < q.replace(/^0+/, "") ? -1 : p.replace(/^0+/, "") > q.replace(/^0+/, "") ? 1 : 0;
      if (byDigits !== 0) return byDigits;
    } else if (p !== q) {
      return p < q ? -1 : 1;
    }
  }
  return Math.sign(x.length - y.length);
}

/**
 * The provider's answer → the dropdown's options: only models that chat, each
 * once, «…latest» aliases first, then newest first (by the version in the id).
 */
export function parseModelList(provider: AiProvider, body: unknown): AiModelOption[] {
  const root = typeof body === "object" && body !== null ? (body as Json) : {};
  let options: AiModelOption[];

  if (provider === "gemini") {
    options = records(root.models)
      .filter((model) => Array.isArray(model.supportedGenerationMethods) && model.supportedGenerationMethods.includes("generateContent"))
      .map((model) => {
        const id = text(model.name).replace(/^models\//, "");
        return { id, label: text(model.displayName) || id };
      })
      .filter((model) => GEMINI_CHAT.test(model.id) && !GEMINI_EXCLUDED.test(model.id));
  } else if (provider === "anthropic") {
    options = records(root.data).map((model) => ({ id: text(model.id), label: text(model.display_name) || text(model.id) }));
  } else if (provider === "openrouter") {
    options = records(root.data)
      .filter((model) => {
        const outputs = (model.architecture as Json | undefined)?.output_modalities;
        return !Array.isArray(outputs) || outputs.includes("text");
      })
      .map((model) => ({ id: text(model.id), label: text(model.name) || text(model.id) }));
  } else if (provider === "openai") {
    options = records(root.data)
      .map((model) => text(model.id))
      .filter((id) => OPENAI_CHAT.test(id) && !OPENAI_EXCLUDED.test(id))
      .map((id) => ({ id, label: id }));
  } else {
    // Ollama, LM Studio…: whatever the server has loaded.
    options = records(root.data).map((model) => ({ id: text(model.id), label: text(model.id) }));
  }

  const seen = new Set<string>();
  const unique = options.filter((option) => option.id !== "" && !seen.has(option.id) && seen.add(option.id));
  const isAlias = (option: AiModelOption) => option.id.endsWith("-latest") || option.id.endsWith("/auto");
  return unique.sort((a, b) => {
    if (isAlias(a) !== isAlias(b)) return isAlias(a) ? -1 : 1;
    return naturalCompare(b.id, a.id);
  });
}
