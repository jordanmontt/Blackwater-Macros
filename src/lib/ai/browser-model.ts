"use client";

import { useSyncExternalStore } from "react";
import type { MLCEngineInterface } from "@mlc-ai/web-llm";
import type { AiMessage } from "@/lib/core/ai-providers";

/**
 * Local AI on the web (D9, phase 10): one small open model run by WebLLM on the
 * browser's GPU (WebGPU), for the Coach only. Nothing leaves the browser once
 * the model is downloaded; the files stay in the browser's cache. WebLLM is
 * imported only when used, so it costs nothing to the normal pages.
 */

export const BROWSER_MODEL = {
  name: "Qwen3 1.7B",
  /** Apache-2.0, published by MLC on Hugging Face (not gated). */
  f16: "Qwen3-1.7B-q4f16_1-MLC",
  f32: "Qwen3-1.7B-q4f32_1-MLC",
  sizeGb: 1,
};

export type BrowserModelState =
  | { status: "checking" }
  /** No WebGPU here (common on phones and some browsers). */
  | { status: "unsupported" }
  | { status: "absent" }
  | { status: "downloading"; progress: number }
  | { status: "ready" }
  | { status: "error"; message: string };

type WebLlm = typeof import("@mlc-ai/web-llm");

let state: BrowserModelState = { status: "checking" };
let modelId: string | null = null;
let engine: MLCEngineInterface | null = null;
let loading: Promise<MLCEngineInterface> | null = null;
let checked = false;
const listeners = new Set<() => void>();

function set(next: BrowserModelState) {
  state = next;
  listeners.forEach((listener) => listener());
}

const loadWebLlm = (): Promise<WebLlm> => import("@mlc-ai/web-llm");

interface GpuNavigator {
  gpu?: { requestAdapter(): Promise<{ features: { has(name: string): boolean } } | null> };
}

/** Which model build this GPU can run, or null without WebGPU. */
async function pickModelId(): Promise<string | null> {
  const gpu = (navigator as unknown as GpuNavigator).gpu;
  if (!gpu) return null;
  const adapter = await gpu.requestAdapter().catch(() => null);
  if (!adapter) return null;
  return adapter.features.has("shader-f16") ? BROWSER_MODEL.f16 : BROWSER_MODEL.f32;
}

/** Looks once for WebGPU and for a model already in this browser's cache. */
export async function checkBrowserModel(): Promise<void> {
  if (checked) return;
  checked = true;
  try {
    modelId = await pickModelId();
    if (!modelId) return set({ status: "unsupported" });
    const webllm = await loadWebLlm();
    set({ status: (await webllm.hasModelInCache(modelId)) ? "ready" : "absent" });
  } catch {
    set({ status: "unsupported" });
  }
}

function load(): Promise<MLCEngineInterface> {
  if (engine) return Promise.resolve(engine);
  if (loading) return loading;
  const id = modelId;
  if (!id) return Promise.reject(new Error("WebGPU not available"));
  loading = loadWebLlm()
    .then((webllm) =>
      webllm.CreateMLCEngine(id, {
        initProgressCallback: (report) => {
          if (state.status !== "ready") set({ status: "downloading", progress: report.progress });
        },
      }),
    )
    .then((created) => {
      engine = created;
      set({ status: "ready" });
      return created;
    })
    .finally(() => {
      loading = null;
    });
  return loading;
}

/** Downloads (first time) and loads the model; the files stay in the browser cache. */
export async function downloadBrowserModel(): Promise<void> {
  set({ status: "downloading", progress: 0 });
  try {
    await load();
  } catch (error) {
    set({ status: "error", message: error instanceof Error ? error.message : String(error) });
  }
}

/** Frees the GPU memory and removes the model files from this browser. */
export async function deleteBrowserModel(): Promise<void> {
  const id = modelId;
  await engine?.unload().catch(() => undefined);
  engine = null;
  if (id) {
    const webllm = await loadWebLlm();
    await webllm.deleteModelAllInfoInCache(id).catch(() => undefined);
  }
  set({ status: "absent" });
}

/** The coach's answer, piece by piece, from the model in this browser. */
export async function* browserChatStream(
  system: string,
  messages: AiMessage[],
  signal?: AbortSignal,
): AsyncGenerator<string> {
  const current = await load();
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
      // Qwen3 «thinks» first by default: slower and not shown anyway.
      extra_body: { enable_thinking: false },
    });
    for await (const chunk of chunks) {
      if (signal?.aborted) break;
      const text = chunk.choices[0]?.delta?.content;
      if (text) yield text;
    }
  } finally {
    signal?.removeEventListener("abort", onAbort);
  }
}

export function getBrowserModelState(): BrowserModelState {
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
  modelId = next.status === "ready" ? BROWSER_MODEL.f16 : null;
  engine = null;
  loading = null;
  checked = next.status !== "checking";
}
