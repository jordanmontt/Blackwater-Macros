"use client";

import { useSyncExternalStore } from "react";
import { DEFAULT_MODELS, isAiConfigured, type AiConfig, type AiProvider } from "@/lib/core/ai-providers";
import { localJsonStore } from "@/lib/local-json-store";

/**
 * Ajustes → IA, kept only in this browser's localStorage: the key never
 * reaches the Blackwater server, it goes straight to the chosen provider.
 * One key and model per provider, so switching back and forth keeps them.
 */
export interface AiSettings {
  provider: AiProvider;
  apiKeys: Partial<Record<AiProvider, string>>;
  /** Empty or missing = the provider's default model. */
  models: Partial<Record<AiProvider, string>>;
  /** Only for the «custom» provider (Ollama, LM Studio…). */
  baseUrl: string;
  /** The coach receives a summary of your data with each question. */
  coachSeesData: boolean;
  /** The coach runs in the cloud or on the model downloaded into this browser. */
  coachEngine: "cloud" | "browser";
  /** Which model to download into this browser (`BROWSER_MODELS` id; empty = the default). */
  browserModel: string;
}

export const AI_PROVIDERS: AiProvider[] = ["gemini", "openai", "anthropic", "openrouter", "custom"];

export const DEFAULT_AI_SETTINGS: AiSettings = {
  provider: "gemini",
  apiKeys: {},
  models: {},
  baseUrl: "",
  coachSeesData: true,
  coachEngine: "cloud",
  browserModel: "",
};

function parse(raw: string | null): AiSettings {
  if (!raw) return DEFAULT_AI_SETTINGS;
  try {
    const value = JSON.parse(raw) as Partial<AiSettings>;
    return {
      provider: AI_PROVIDERS.includes(value.provider as AiProvider) ? (value.provider as AiProvider) : "gemini",
      apiKeys: typeof value.apiKeys === "object" && value.apiKeys ? value.apiKeys : {},
      models: typeof value.models === "object" && value.models ? value.models : {},
      baseUrl: typeof value.baseUrl === "string" ? value.baseUrl : "",
      coachSeesData: value.coachSeesData !== false,
      coachEngine: value.coachEngine === "browser" ? "browser" : "cloud",
      browserModel: typeof value.browserModel === "string" ? value.browserModel : "",
    };
  } catch {
    return DEFAULT_AI_SETTINGS;
  }
}

const store = localJsonStore("bw:ai", parse);

/** The stored settings; the same object until they change (for useSyncExternalStore). */
export const getAiSettings = store.get;

export function saveAiSettings(update: (current: AiSettings) => AiSettings): void {
  store.set(update(store.get()));
}

export function useAiSettings(): AiSettings {
  return useSyncExternalStore(store.subscribe, store.get, () => DEFAULT_AI_SETTINGS);
}

/** What the provider calls need, from the settings. */
export function aiConfigOf(settings: AiSettings): AiConfig {
  return {
    provider: settings.provider,
    apiKey: settings.apiKeys[settings.provider] ?? "",
    model: settings.models[settings.provider]?.trim() || DEFAULT_MODELS[settings.provider],
    baseUrl: settings.baseUrl,
  };
}

export function isAiReady(settings: AiSettings): boolean {
  return isAiConfigured(aiConfigOf(settings));
}

/** The coach can answer: with the cloud key, or with the model in this browser. */
export function isCoachReady(settings: AiSettings, browserModelReady: boolean): boolean {
  return settings.coachEngine === "browser" ? browserModelReady : isAiReady(settings);
}
