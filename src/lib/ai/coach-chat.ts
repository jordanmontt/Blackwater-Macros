"use client";

import { useSyncExternalStore } from "react";
import type { AiMessage } from "@/lib/core/ai-providers";
import { buildCoachSystemPrompt } from "@/lib/core/coach";
import { AiError, aiStream, type AiFailure } from "@/lib/ai/client";
import { loadCoachContext } from "@/lib/ai/coach-data";
import { aiLanguage } from "@/lib/ai/estimate";
import { browserChatStream } from "@/lib/ai/browser-model";
import { aiConfigOf, getAiSettings } from "@/lib/ai/settings";

export interface ChatMessage {
  id: number;
  role: "user" | "assistant";
  text: string;
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
 * The Coach conversation (D3): module memory only. It survives switching tabs
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

/** The earlier turns the model sees: failed answers and their questions are left out. */
export function historyForModel(messages: ChatMessage[]): AiMessage[] {
  const turns: AiMessage[] = [];
  messages.forEach((message, index) => {
    if (message.error) return;
    const next = messages[index + 1];
    // A question without an answer (failed or stopped before any text) is left out too.
    if (message.role === "user" && next && (next.error || next.text.trim() === "")) return;
    if (message.role === "assistant" && message.text.trim() === "") return;
    turns.push({ role: message.role, text: message.text });
  });
  const recent = turns.slice(-COACH_HISTORY_MESSAGES);
  // Providers want the conversation to start with the user.
  while (recent.length > 0 && recent[0].role !== "user") recent.shift();
  return recent;
}

export async function sendCoachMessage(text: string): Promise<void> {
  const question = text.trim();
  if (question === "" || state.streaming) return;
  const history = historyForModel(state.messages);
  set({
    messages: [
      ...state.messages,
      { id: nextId++, role: "user", text: question },
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
    const messages: AiMessage[] = [...history, { role: "user", text: question }];
    const stream =
      settings.coachEngine === "browser"
        ? browserChatStream(system, messages, signal)
        : aiStream(aiConfigOf(settings), { system, messages, json: false, stream: true, maxTokens: 4096 }, signal);
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
