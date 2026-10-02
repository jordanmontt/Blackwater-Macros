"use client";

import { useSyncExternalStore } from "react";
import type { AiImage, AiMessage } from "@/lib/core/ai-providers";
import { buildCoachSystemPrompt } from "@/lib/core/coach";
import { AiError, aiStream, type AiFailure } from "@/lib/ai/client";
import { loadCoachContext } from "@/lib/ai/coach-data";
import { aiLanguage } from "@/lib/ai/estimate";
import { browserChatStream } from "@/lib/ai/browser-model";
import { aiConfigOf, getAiSettings } from "@/lib/ai/settings";
import { MAX_PHOTOS } from "@/lib/ai/images";
import { t } from "@/i18n";

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  text: string;
  /** Photos sent with a question: small JPEGs, in memory only (never stored). */
  images?: AiImage[];
  /** Set on an assistant message whose answer failed. */
  error?: AiFailure;
  /** The provider's (or the browser model's) own words about the failure. */
  errorDetail?: string;
}

export interface ChatState {
  messages: ChatMessage[];
  streaming: boolean;
}

/** Earlier turns sent with each question (keeps requests small on free tiers). */
export const COACH_HISTORY_MESSAGES = 20;

/**
 * Output budget of a coach answer. Reasoning models (the default Gemini Flash and
 * GPT-5 mini) spend hidden thinking tokens from it: at 4096 long answers were cut
 * mid-sentence. Every current default model accepts 8192.
 */
export const COACH_MAX_TOKENS = 8192;

/**
 * The Coach conversation: module memory only. It survives switching tabs
 * and is gone when the page is reloaded or closed; nothing is stored anywhere.
 */
let state: ChatState = { messages: [], streaming: false };
let nextId = 1;
let controller: AbortController | null = null;
const listeners = new Set<() => void>();

function set(next: ChatState) {
  state = next;
  listeners.forEach((listener) => listener());
}

function patchLast(update: (message: ChatMessage) => ChatMessage) {
  const messages = state.messages.slice();
  messages[messages.length - 1] = update(messages[messages.length - 1]);
  set({ ...state, messages });
}

export function getChatState(): ChatState {
  return state;
}

/**
 * The earlier turns the model sees: failed answers and their questions are left
 * out. Photos travel again with their question so a follow-up («¿y cuánta
 * proteína tiene?») still sees them, but only the newest [MAX_PHOTOS] of them.
 */
export function historyForModel(messages: ChatMessage[], newImages = 0): AiMessage[] {
  const turns: AiMessage[] = [];
  messages.forEach((message, index) => {
    if (message.error) return;
    const next = messages[index + 1];
    // A question without an answer (failed or stopped before any text) is left out too.
    if (message.role === "user" && next && (next.error || next.text.trim() === "")) return;
    if (message.role === "assistant" && message.text.trim() === "") return;
    turns.push({ role: message.role, text: modelText(message), images: message.images });
  });
  const recent = turns.slice(-COACH_HISTORY_MESSAGES);
  let room = Math.max(0, MAX_PHOTOS - newImages);
  for (let i = recent.length - 1; i >= 0; i--) {
    const images = recent[i].images ?? [];
    const kept = images.slice(0, room);
    room -= kept.length;
    recent[i] = kept.length > 0 ? { ...recent[i], images: kept } : { role: recent[i].role, text: recent[i].text };
  }
  // Providers want the conversation to start with the user.
  while (recent.length > 0 && recent[0].role !== "user") recent.shift();
  return recent;
}

/** A photo sent without words still needs a question for the model. */
function modelText(message: ChatMessage): string {
  return message.text.trim() === "" && message.images?.length ? t.coach.photoPrompt : message.text;
}

export async function sendCoachMessage(text: string, images: AiImage[] = []): Promise<void> {
  const typed = text.trim();
  if ((typed === "" && images.length === 0) || state.streaming) return;
  const history = historyForModel(state.messages, images.length);
  const asked: ChatMessage = { id: nextId++, role: "user", text: typed, images: images.length > 0 ? images : undefined };
  const question = modelText(asked);
  set({
    messages: [
      ...state.messages,
      asked,
      { id: nextId++, role: "assistant", text: "" },
    ],
    streaming: true,
  });
  controller = new AbortController();
  const signal = controller.signal;
  try {
    const settings = getAiSettings();
    const context = settings.coachSeesData ? await loadCoachContext() : null;
    const system = buildCoachSystemPrompt(aiLanguage(), context);
    const messages: AiMessage[] = [...history, { role: "user", text: question, images: asked.images }];
    const stream =
      settings.coachEngine === "browser"
        ? browserChatStream(system, messages, signal)
        : aiStream(aiConfigOf(settings), { system, messages, json: false, stream: true, maxTokens: COACH_MAX_TOKENS }, signal);
    for await (const piece of stream) {
      if (signal.aborted) break;
      patchLast((message) => ({ ...message, text: message.text + piece }));
    }
    if (!signal.aborted && state.messages.at(-1)?.text.trim() === "") patchLast((message) => ({ ...message, error: "empty" }));
  } catch (error) {
    if (!signal.aborted) {
      const kind: AiFailure = error instanceof AiError ? error.kind : "provider";
      const detail = error instanceof AiError ? error.detail : error instanceof Error ? error.message.slice(0, 200) : "";
      patchLast((message) => ({ ...message, error: kind, errorDetail: detail }));
    }
  } finally {
    if (controller?.signal === signal) controller = null;
    if (!signal.aborted || state.streaming) set({ ...state, streaming: false });
  }
}

/** Stops the answer being written; what arrived so far stays. */
export function stopCoach(): void {
  controller?.abort();
  controller = null;
  set({ ...state, streaming: false });
}

/** «Nueva conversación». */
export function resetCoach(): void {
  controller?.abort();
  controller = null;
  set({ messages: [], streaming: false });
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const EMPTY: ChatState = { messages: [], streaming: false };

export function useCoachChat(): ChatState {
  return useSyncExternalStore(subscribe, getChatState, () => EMPTY);
}
