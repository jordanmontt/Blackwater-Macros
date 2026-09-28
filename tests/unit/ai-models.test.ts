import { describe, expect, it } from "vitest";
import { buildModelListRequest, naturalCompare, parseModelList } from "../../src/lib/core/ai-models";
import type { AiConfig } from "../../src/lib/core/ai-providers";

/**
 * The model dropdown lists what the provider says the key can use (never a
 * real call: answers shaped like each API's docs). The Kotlin mirror
 * (`AiModelsTest.kt`) checks the same requests and the same options in order.
 */

function config(provider: AiConfig["provider"], extra: Partial<AiConfig> = {}): AiConfig {
  return { provider, apiKey: "test-key", model: "", baseUrl: "", ...extra };
}

const GEMINI = {
  models: [
    { name: "models/gemini-2.5-flash", displayName: "Gemini 2.5 Flash", supportedGenerationMethods: ["generateContent", "countTokens"] },
    { name: "models/gemini-3.8-flash", displayName: "Gemini 3.8 Flash", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemini-3-flash", displayName: "Gemini 3 Flash", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemini-flash-latest", displayName: "Gemini Flash Latest", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemini-2.5-flash-lite", displayName: "Gemini 2.5 Flash-Lite", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemini-2.5-flash-preview-tts", displayName: "Gemini 2.5 Flash TTS", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemini-2.5-flash-image", displayName: "Nano Banana", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemma-3-27b-it", displayName: "Gemma 3 27B", supportedGenerationMethods: ["generateContent"] },
    { name: "models/gemini-embedding-001", displayName: "Gemini Embedding", supportedGenerationMethods: ["embedContent"] },
    { name: "models/text-embedding-004", displayName: "Text Embedding", supportedGenerationMethods: ["embedContent"] },
  ],
};

describe("buildModelListRequest", () => {
  it("asks each provider with its own key header", () => {
    expect(buildModelListRequest(config("gemini"))).toEqual({
      url: "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
      headers: { "x-goog-api-key": "test-key" },
    });
    expect(buildModelListRequest(config("anthropic"))).toEqual({
      url: "https://api.anthropic.com/v1/models?limit=1000",
      headers: {
        "x-api-key": "test-key",
        "anthropic-version": "2023-06-01",
        "anthropic-dangerous-direct-browser-access": "true",
      },
    });
    expect(buildModelListRequest(config("openai"))).toEqual({
      url: "https://api.openai.com/v1/models",
      headers: { Authorization: "Bearer test-key" },
    });
    expect(buildModelListRequest(config("openrouter")).url).toBe("https://openrouter.ai/api/v1/models");
    expect(buildModelListRequest(config("custom", { apiKey: "", baseUrl: "http://192.168.1.10:11434/v1/" }))).toEqual({
      url: "http://192.168.1.10:11434/v1/models",
      headers: {},
    });
  });
});

describe("parseModelList", () => {
  it("Gemini: only chat models, without image, speech, Gemma or embeddings; aliases first, newest first", () => {
    expect(parseModelList("gemini", GEMINI)).toEqual([
      { id: "gemini-flash-latest", label: "Gemini Flash Latest" },
      { id: "gemini-3.8-flash", label: "Gemini 3.8 Flash" },
      { id: "gemini-3-flash", label: "Gemini 3 Flash" },
      { id: "gemini-2.5-flash-lite", label: "Gemini 2.5 Flash-Lite" },
      { id: "gemini-2.5-flash", label: "Gemini 2.5 Flash" },
    ]);
  });

  it("OpenAI: chat models only, by version", () => {
    const body = {
      data: [
        { id: "gpt-5-mini" },
        { id: "gpt-5.1" },
        { id: "o4-mini" },
        { id: "gpt-4o-audio-preview" },
        { id: "gpt-realtime" },
        { id: "text-embedding-3-small" },
        { id: "dall-e-3" },
        { id: "whisper-1" },
        { id: "gpt-image-1" },
      ],
    };
    expect(parseModelList("openai", body).map((model) => model.id)).toEqual(["o4-mini", "gpt-5.1", "gpt-5-mini"]);
  });

  it("Anthropic and OpenRouter use their display names; OpenRouter keeps text models, «auto» first", () => {
    expect(
      parseModelList("anthropic", {
        data: [
          { id: "claude-haiku-4-5", display_name: "Claude Haiku 4.5" },
          { id: "claude-sonnet-4-5", display_name: "Claude Sonnet 4.5" },
        ],
      }),
    ).toEqual([
      { id: "claude-sonnet-4-5", label: "Claude Sonnet 4.5" },
      { id: "claude-haiku-4-5", label: "Claude Haiku 4.5" },
    ]);
    expect(
      parseModelList("openrouter", {
        data: [
          { id: "google/gemini-3-flash", name: "Google: Gemini 3 Flash", architecture: { output_modalities: ["text"] } },
          { id: "openrouter/auto", name: "Auto Router", architecture: { output_modalities: ["text"] } },
          { id: "black-forest-labs/flux", name: "FLUX", architecture: { output_modalities: ["image"] } },
        ],
      }),
    ).toEqual([
      { id: "openrouter/auto", label: "Auto Router" },
      { id: "google/gemini-3-flash", label: "Google: Gemini 3 Flash" },
    ]);
  });

  it("a compatible server lists what it has loaded; a broken answer gives no options", () => {
    expect(parseModelList("custom", { data: [{ id: "llama3.2" }, { id: "qwen3:8b" }, { id: "llama3.2" }] })).toEqual([
      { id: "qwen3:8b", label: "qwen3:8b" },
      { id: "llama3.2", label: "llama3.2" },
    ]);
    expect(parseModelList("gemini", null)).toEqual([]);
    expect(parseModelList("openai", { error: "nope" })).toEqual([]);
  });
});

describe("naturalCompare", () => {
  it("compares numbers as numbers", () => {
    const sorted = ["gpt-5.10", "gpt-5.9", "gemini-3.1-pro", "gemini-3.8-flash", "gemini-3-flash"].sort(naturalCompare);
    expect(sorted).toEqual(["gemini-3-flash", "gemini-3.1-pro", "gemini-3.8-flash", "gpt-5.9", "gpt-5.10"]);
  });
});
