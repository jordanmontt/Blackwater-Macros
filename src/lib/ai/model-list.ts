"use client";

import { useSyncExternalStore } from "react";
import { buildModelListRequest, parseModelList, type AiModelOption } from "@/lib/core/ai-models";
import { aiErrorDetail, aiErrorKind, isAiConfigured, type AiConfig, type AiProvider } from "@/lib/core/ai-providers";
import { AiError } from "@/lib/ai/client";

/**
 * The models the user's key can use, asked to the provider (`core/ai-models.ts`)
 * and kept in this browser, so the dropdown in Ajustes → IA works offline and
 * opens instantly. Refreshed when the app opens (at most once a day), when the
 * key changes and with «Actualizar». Like the key, it never reaches our server.
 */

export interface ModelList {
  models: AiModelOption[];
  fetchedAt: string;
  /** Which key/server the list is for (a hash, never the key itself). */
  fingerprint: string;
}

export const MODEL_LIST_MAX_AGE_MS = 24 * 60 * 60 * 1000;
const STORAGE_KEY = "bw:ai-models";
const listeners = new Set<() => void>();
const inFlight = new Map<string, Promise<ModelList>>();
let cachedRaw: string | null | undefined;
let cachedLists: Partial<Record<AiProvider, ModelList>> = {};

/** Bump when `parseModelList` filters differently: older cached lists are then asked again. */
const LIST_FORMAT = 2;

/** FNV-1a: enough to notice that the key or server changed, without storing either. */
export function configFingerprint(config: AiConfig): string {
  let hash = 0x811c9dc5;
  for (const char of `${LIST_FORMAT}|${config.provider}|${config.apiKey.trim()}|${config.baseUrl.trim()}`) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16);
}

function readRaw(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function getLists(): Partial<Record<AiProvider, ModelList>> {
  const raw = readRaw();
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    try {
      cachedLists = raw ? (JSON.parse(raw) as Partial<Record<AiProvider, ModelList>>) : {};
    } catch {
      cachedLists = {};
    }
  }
  return cachedLists;
}

function save(provider: AiProvider, list: ModelList) {
  const next = { ...getLists(), [provider]: list };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    cachedRaw = JSON.stringify(next);
    cachedLists = next;
  }
  listeners.forEach((listener) => listener());
}

/** The cached list for this key, or null (none yet, or it was for another key). */
export function cachedModelList(config: AiConfig): ModelList | null {
  const list = getLists()[config.provider];
  return list && list.fingerprint === configFingerprint(config) ? list : null;
}

/**
 * Asks the provider again when [force]d, when there is no list for this key,
 * or when it is older than a day. Throws [AiError] (offline, invalid key…).
 */
export async function refreshModelList(config: AiConfig, force = false): Promise<ModelList | null> {
  if (!isAiConfigured(config)) return null;
  const cached = cachedModelList(config);
  if (!force && cached && Date.now() - Date.parse(cached.fetchedAt) < MODEL_LIST_MAX_AGE_MS) return cached;
  const fingerprint = configFingerprint(config);
  const running = inFlight.get(fingerprint);
  if (running) return running;
  const request = (async () => {
    const { url, headers } = buildModelListRequest(config);
    let response: Response;
    try {
      response = await fetch(url, { headers });
    } catch (error) {
      throw new AiError("offline", error instanceof Error ? error.message : "");
    }
    if (!response.ok) {
      const body = await response.text().catch(() => "");
      throw new AiError(aiErrorKind(response.status, body), `${response.status}: ${aiErrorDetail(body)}`);
    }
    const list: ModelList = {
      models: parseModelList(config.provider, await response.json().catch(() => null)),
      fetchedAt: new Date().toISOString(),
      fingerprint,
    };
    save(config.provider, list);
    return list;
  })();
  inFlight.set(fingerprint, request);
  try {
    return await request;
  } finally {
    inFlight.delete(fingerprint);
  }
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

const NONE: Partial<Record<AiProvider, ModelList>> = {};

/** The cached list for [config]'s key, updating when a refresh lands. */
export function useModelList(config: AiConfig): ModelList | null {
  const lists = useSyncExternalStore(subscribe, getLists, () => NONE);
  const list = lists[config.provider];
  return list && list.fingerprint === configFingerprint(config) ? list : null;
}
