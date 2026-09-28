"use client";

import { useSyncExternalStore } from "react";
import type { MLCEngineInterface } from "@mlc-ai/web-llm";
import type { AiMessage } from "@/lib/core/ai-providers";
import { getAiSettings, saveAiSettings } from "@/lib/ai/settings";

/**
 * Local AI on the web: a small open model run by WebLLM on the browser's GPU
 * (WebGPU — the only fast way a web page can run a model; there is no CPU path
 * as on Android), for the Coach only. Nothing leaves the browser once the model
 * is downloaded; the files stay in the browser's cache. WebLLM is imported only
 * when used, so it costs nothing to the normal pages. One model at a time, as
 * on Android.
 */

export interface BrowserModelSpec {
  id: string;
  name: string;
  /** WebLLM builds (Apache-2.0, published by MLC on Hugging Face, not gated). */
  f16: string;
  f32: string;
  /** Download size, for the warning. */
  sizeGb: number;
  /** Offered for download; `false` = only recognised when already in the cache. */
  offered: boolean;
}

/**
 * Tried in a browser on 2026-09-28 with «¿Cuántos macros tienen 3 plátanos?»:
 * Qwen3.5 2B answers sensibly (with slips); Qwen3.5 0.8B wrote wrong numbers and
 * looped, so it is not offered. The 4B is noticeably better but needs ≈4 GB.
 */
export const BROWSER_MODELS: BrowserModelSpec[] = [
  { id: "qwen3.5-2b", name: "Qwen3.5 2B", f16: "Qwen3.5-2B-q4f16_1-MLC", f32: "Qwen3.5-2B-q4f32_1-MLC", sizeGb: 1.1, offered: true },
  { id: "qwen3.5-4b", name: "Qwen3.5 4B", f16: "Qwen3.5-4B-q4f16_1-MLC", f32: "Qwen3.5-4B-q4f32_1-MLC", sizeGb: 2.4, offered: true },
  // The first web model: still recognised (and deletable) where it was downloaded.
  { id: "qwen3-1.7b", name: "Qwen3 1.7B", f16: "Qwen3-1.7B-q4f16_1-MLC", f32: "Qwen3-1.7B-q4f32_1-MLC", sizeGb: 1, offered: false },
];

export const DEFAULT_BROWSER_MODEL = BROWSER_MODELS[0];

/**
 * WebLLM's shaders use up to 10 storage buffers per stage; Firefox's WebGPU
 * allows 9 and fails half-way through the download («requested=10, limit=9»).
 */
export const MIN_STORAGE_BUFFERS = 10;

export type BrowserModelState =
  | { status: "checking" }
  /** No WebGPU here (Firefox on Android, older browsers), or one too limited (Firefox desktop). */
  | { status: "unsupported"; reason: "no-webgpu" | "limited" }
  | { status: "absent"; model: BrowserModelSpec }
  | { status: "downloading"; model: BrowserModelSpec; progress: number }
  | { status: "ready"; model: BrowserModelSpec }
  | { status: "error"; model: BrowserModelSpec; message: string };

type WebLlm = typeof import("@mlc-ai/web-llm");

let state: BrowserModelState = { status: "checking" };
let useF16 = true;
let engine: MLCEngineInterface | null = null;
let engineModelId: string | null = null;
let loading: Promise<MLCEngineInterface> | null = null;
let checked = false;
const listeners = new Set<() => void>();

function set(next: BrowserModelState) {
  state = next;
  listeners.forEach((listener) => listener());
}

const loadWebLlm = (): Promise<WebLlm> => import("@mlc-ai/web-llm");

interface GpuAdapter {
  features: { has(name: string): boolean };
  limits?: { maxStorageBuffersPerShaderStage?: number };
}

interface GpuNavigator {
  gpu?: { requestAdapter(): Promise<GpuAdapter | null> };
}

function buildOf(model: BrowserModelSpec): string {
  return useF16 ? model.f16 : model.f32;
}

/** The model picked in Ajustes (only offered ones), else the default. */
function chosenModel(): BrowserModelSpec {
  const id = getAiSettings().browserModel;
  return BROWSER_MODELS.find((model) => model.id === id && model.offered) ?? DEFAULT_BROWSER_MODEL;
}

/** Looks once for WebGPU (and its limits) and for a model already in this browser's cache. */
export async function checkBrowserModel(): Promise<void> {
  if (checked) return;
  checked = true;
  try {
    const gpu = (navigator as unknown as GpuNavigator).gpu;
    const adapter = gpu ? await gpu.requestAdapter().catch(() => null) : null;
    if (!adapter) return set({ status: "unsupported", reason: "no-webgpu" });
    const buffers = adapter.limits?.maxStorageBuffersPerShaderStage;
    if (buffers !== undefined && buffers < MIN_STORAGE_BUFFERS) return set({ status: "unsupported", reason: "limited" });
    useF16 = adapter.features.has("shader-f16");
    const webllm = await loadWebLlm();
    for (const model of BROWSER_MODELS) {
      if (await webllm.hasModelInCache(buildOf(model))) return set({ status: "ready", model });
    }
    set({ status: "absent", model: chosenModel() });
  } catch {
    set({ status: "unsupported", reason: "no-webgpu" });
  }
}

/** Choose which model to download (while none is in this browser). */
export function selectBrowserModel(id: string): void {
  const model = BROWSER_MODELS.find((candidate) => candidate.id === id && candidate.offered);
  if (!model || (state.status !== "absent" && state.status !== "error")) return;
  saveAiSettings((current) => ({ ...current, browserModel: model.id }));
  set({ status: "absent", model });
}

function load(model: BrowserModelSpec): Promise<MLCEngineInterface> {
  const id = buildOf(model);
  if (engine && engineModelId === id) return Promise.resolve(engine);
  if (loading) return loading;
  loading = loadWebLlm()
    .then((webllm) =>
      webllm.CreateMLCEngine(id, {
        initProgressCallback: (report) => {
          if (state.status !== "ready") set({ status: "downloading", model, progress: report.progress });
        },
      }),
    )
    .then((created) => {
      engine = created;
      engineModelId = id;
      set({ status: "ready", model });
      return created;
    })
    .finally(() => {
      loading = null;
    });
  return loading;
}

/** Downloads (first time) and loads the chosen model; the files stay in the browser cache. */
export async function downloadBrowserModel(): Promise<void> {
  if (state.status !== "absent" && state.status !== "error") return;
  const model = state.model;
  set({ status: "downloading", model, progress: 0 });
  try {
    await load(model);
  } catch (error) {
    set({ status: "error", model, message: error instanceof Error ? error.message : String(error) });
  }
}

/** Frees the GPU memory and removes the model files from this browser. */
export async function deleteBrowserModel(): Promise<void> {
  if (state.status !== "ready") return;
  const model = state.model;
  await engine?.unload().catch(() => undefined);
  engine = null;
  engineModelId = null;
  const webllm = await loadWebLlm();
  await webllm.deleteModelAllInfoInCache(buildOf(model)).catch(() => undefined);
  set({ status: "absent", model: chosenModel() });
}

/**
 * Even with thinking off, Qwen writes an empty `<think>…</think>` block first:
 * the pieces are held back until it is closed (or clearly absent) and the
 * block is dropped, with the blank lines after it.
 */
export function withoutThinking(): (piece: string) => string {
  let buffer = "";
  let done = false;
  return (piece) => {
    if (done) return piece;
    buffer += piece;
    const trimmed = buffer.trimStart();
    if (!"<think>".startsWith(trimmed.slice(0, 7)) ) {
      done = true;
      return buffer;
    }
    const end = buffer.indexOf("</think>");
    if (end === -1) return "";
    done = true;
    return buffer.slice(end + "</think>".length).trimStart();
  };
}

/** The coach's answer, piece by piece, from the model in this browser. */
export async function* browserChatStream(
  system: string,
  messages: AiMessage[],
  signal?: AbortSignal,
): AsyncGenerator<string> {
  if (state.status !== "ready") throw new Error("No model in this browser");
  const current = await load(state.model);
  const onAbort = () => current.interruptGenerate();
  signal?.addEventListener("abort", onAbort);
  try {
    const chunks = await current.chat.completions.create({
      messages: [
        { role: "system", content: system },
        ...messages.map((message) => ({ role: message.role, content: message.text })),
      ],
      stream: true,
      max_tokens: 1024,
      // Small models wander into nonsense with the default (random) sampling.
      temperature: 0.3,
      top_p: 0.9,
      // …and repeat whole paragraphs in a loop without a small penalty.
      frequency_penalty: 0.3,
      // Qwen «thinks» first by default: slower and not shown anyway.
      extra_body: { enable_thinking: false },
    });
    const visible = withoutThinking();
    for await (const chunk of chunks) {
      if (signal?.aborted) break;
      const text = visible(chunk.choices[0]?.delta?.content ?? "");
      if (text) yield text;
    }
  } finally {
    signal?.removeEventListener("abort", onAbort);
  }
}

function getBrowserModelState(): BrowserModelState {
  return state;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const CHECKING: BrowserModelState = { status: "checking" };

export function useBrowserModel(): BrowserModelState {
  return useSyncExternalStore(subscribe, getBrowserModelState, () => CHECKING);
}

/** For tests only. */
export function resetBrowserModelForTests(next: BrowserModelState = { status: "checking" }): void {
  state = next;
  useF16 = true;
  engine = null;
  engineModelId = null;
  loading = null;
  checked = next.status !== "checking";
}
