"use client";

import { useSyncExternalStore } from "react";
import { DEFAULT_MODELS, isAiConfigured, type AiConfig, type AiProvider } from "@/lib/core/ai-providers";

/**
 * Ajustes → IA, kept only in this browser's localStorage (D10): the key never
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
  /** D11: the coach receives a summary of your data with each question. */
  coachSeesData: boolean;
  /** D5/D9: the coach runs in the cloud or on the model downloaded into this browser. */
  coachEngine: "cloud" | "browser";
}

export const AI_PROVIDERS: AiProvider[] = ["gemini", "openai", "anthropic", "openrouter", "custom"];

export const DEFAULT_AI_SETTINGS: AiSettings = {
  provider: "gemini",
  apiKeys: {},
  models: {},
  baseUrl: "",
  coachSeesData: true,
  coachEngine: "cloud",
};

const STORAGE_KEY = "bw:ai";
const listeners = new Set<() => void>();
let cachedRaw: string | null | undefined;
let cachedSettings: AiSettings = DEFAULT_AI_SETTINGS;

function readRaw(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

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
    };
  } catch {
    return DEFAULT_AI_SETTINGS;
  }
}

/** The stored settings; the same object until they change (for useSyncExternalStore). */
export function getAiSettings(): AiSettings {
  const raw = readRaw();
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    cachedSettings = parse(raw);
  }
  return cachedSettings;
}

export function saveAiSettings(update: (current: AiSettings) => AiSettings): void {
  const next = update(getAiSettings());
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Private mode / storage full: the settings only last for this page.
    cachedRaw = JSON.stringify(next);
    cachedSettings = next;
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function useAiSettings(): AiSettings {
  return useSyncExternalStore(subscribe, getAiSettings, () => DEFAULT_AI_SETTINGS);
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

/** The coach can answer: with the cloud key, or with the model in this browser (D9). */
export function isCoachReady(settings: AiSettings, browserModelReady: boolean): boolean {
  return settings.coachEngine === "browser" ? browserModelReady : isAiReady(settings);
}
