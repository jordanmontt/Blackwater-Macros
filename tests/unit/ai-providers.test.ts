import { describe, expect, it } from "vitest";
import {
  aiErrorKind,
  buildAiRequest,
  DEFAULT_MODELS,
  isAiConfigured,
  parseAiResponse,
  parseAiStreamLine,
  type AiConfig,
  type AiInput,
} from "../../src/lib/core/ai-providers";

/**
 * Requests and answers per provider, from the public API docs (never a real
 * call). The Kotlin mirror asserts the same bodies byte for byte.
 */

const PHOTO: AiInput = {
  system: "Eres un nutricionista.",
  messages: [{ role: "user", text: "Mi comida", images: [{ mimeType: "image/jpeg", data: "AAAA" }] }],
  json: true,
  stream: false,
  maxTokens: 1024,
};

const CHAT: AiInput = {
  system: "Coach",
  messages: [
    { role: "user", text: "Hola" },
    { role: "assistant", text: "¿Qué tal?" },
    { role: "user", text: "¿Qué ceno?" },
  ],
  json: false,
  stream: true,
  maxTokens: 512,
};

function config(provider: AiConfig["provider"], extra: Partial<AiConfig> = {}): AiConfig {
  return { provider, apiKey: "test-key", model: "", baseUrl: "", ...extra };
}

describe("buildAiRequest", () => {
  it("Gemini: key in a header, JSON mode, photos as inline data, default model", () => {
    const request = buildAiRequest(config("gemini"), PHOTO);
    expect(request.url).toBe(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
    );
    expect(request.headers).toEqual({ "Content-Type": "application/json", "x-goog-api-key": "test-key" });
    expect(request.body).toBe(
      `{"systemInstruction":{"parts":[{"text":"Eres un nutricionista."}]},"contents":[{"role":"user","parts":[{"inline_data":{"mime_type":"image/jpeg","data":"AAAA"}},{"text":"Mi comida"}]}],"generationConfig":{"maxOutputTokens":1024,"responseMimeType":"application/json"}}`,
    );
  });

  it("Gemini: streaming uses server-sent events and «model» for the assistant", () => {
    const request = buildAiRequest(config("gemini", { model: "gemini-3.5-flash" }), CHAT);
    expect(request.url).toBe(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse",
    );
    expect(request.body).toBe(
      `{"systemInstruction":{"parts":[{"text":"Coach"}]},"contents":[{"role":"user","parts":[{"text":"Hola"}]},{"role":"model","parts":[{"text":"¿Qué tal?"}]},{"role":"user","parts":[{"text":"¿Qué ceno?"}]}],"generationConfig":{"maxOutputTokens":512}}`,
    );
  });

  it("OpenAI: bearer key, system message, images as data URLs, JSON object mode", () => {
    const request = buildAiRequest(config("openai"), PHOTO);
    expect(request.url).toBe("https://api.openai.com/v1/chat/completions");
    expect(request.headers).toEqual({ "Content-Type": "application/json", Authorization: "Bearer test-key" });
    expect(request.body).toBe(
      `{"model":"${DEFAULT_MODELS.openai}","messages":[{"role":"system","content":"Eres un nutricionista."},{"role":"user","content":[{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,AAAA"}},{"type":"text","text":"Mi comida"}]}],"stream":false,"max_completion_tokens":1024,"response_format":{"type":"json_object"}}`,
    );
  });

  it("OpenRouter and custom servers use the same format; a custom server may have no key", () => {
    expect(buildAiRequest(config("openrouter"), CHAT).url).toBe("https://openrouter.ai/api/v1/chat/completions");
    const local = buildAiRequest(
      config("custom", { apiKey: " ", baseUrl: "http://192.168.1.10:11434/v1/", model: "gemma3:4b" }),
      CHAT,
    );
    expect(local.url).toBe("http://192.168.1.10:11434/v1/chat/completions");
    expect(local.headers).toEqual({ "Content-Type": "application/json" });
    expect(local.body).toBe(
      `{"model":"gemma3:4b","messages":[{"role":"system","content":"Coach"},{"role":"user","content":"Hola"},{"role":"assistant","content":"¿Qué tal?"},{"role":"user","content":"¿Qué ceno?"}],"stream":true,"max_tokens":512}`,
    );
  });

  it("Anthropic: x-api-key, version, browser access header, images as base64 blocks", () => {
    const request = buildAiRequest(config("anthropic"), PHOTO);
    expect(request.url).toBe("https://api.anthropic.com/v1/messages");
    expect(request.headers).toEqual({
      "Content-Type": "application/json",
      "x-api-key": "test-key",
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true",
    });
    expect(request.body).toBe(
      `{"model":"claude-haiku-4-5","max_tokens":1024,"system":"Eres un nutricionista.\\nReply with JSON only.","messages":[{"role":"user","content":[{"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"AAAA"}},{"type":"text","text":"Mi comida"}]}],"stream":false}`,
    );
  });
});

describe("parseAiResponse", () => {
  it("reads each provider's answer text", () => {
    expect(
      parseAiResponse("gemini", {
        candidates: [{ content: { role: "model", parts: [{ text: '{"title":' }, { text: '"Pasta"}' }] } }],
      }),
    ).toBe('{"title":"Pasta"}');
    expect(
      parseAiResponse("openai", { choices: [{ index: 0, message: { role: "assistant", content: "Hola" } }] }),
    ).toBe("Hola");
    expect(
      parseAiResponse("anthropic", {
        content: [
          { type: "thinking", thinking: "…" },
          { type: "text", text: "Hola" },
        ],
      }),
    ).toBe("Hola");
  });

  it("returns an empty text for unexpected shapes", () => {
    expect(parseAiResponse("gemini", { candidates: [] })).toBe("");
    expect(parseAiResponse("openrouter", { error: { message: "x" } })).toBe("");
    expect(parseAiResponse("anthropic", null)).toBe("");
  });
});

describe("parseAiStreamLine", () => {
  it("reads the text deltas of each provider", () => {
    expect(parseAiStreamLine("gemini", 'data: {"candidates":[{"content":{"parts":[{"text":"Ho"}]}}]}')).toBe("Ho");
    expect(parseAiStreamLine("custom", 'data: {"choices":[{"delta":{"content":"la"}}]}')).toBe("la");
    expect(
      parseAiStreamLine("anthropic", 'data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}'),
    ).toBe("!");
  });

  it("ignores events without text", () => {
    expect(parseAiStreamLine("openai", "data: [DONE]")).toBeNull();
    expect(parseAiStreamLine("openai", 'data: {"choices":[{"delta":{"role":"assistant"}}]}')).toBeNull();
    expect(parseAiStreamLine("anthropic", "event: message_start")).toBeNull();
    expect(parseAiStreamLine("anthropic", 'data: {"type":"ping"}')).toBeNull();
    expect(parseAiStreamLine("openrouter", ": OPENROUTER PROCESSING")).toBeNull();
    expect(parseAiStreamLine("gemini", "data: {not json")).toBeNull();
  });
});

describe("isAiConfigured and aiErrorKind", () => {
  it("needs a key, or a server and a model for custom servers", () => {
    expect(isAiConfigured(config("gemini", { apiKey: "" }))).toBe(false);
    expect(isAiConfigured(config("gemini"))).toBe(true);
    expect(isAiConfigured(config("custom", { apiKey: "", baseUrl: "http://localhost:11434/v1" }))).toBe(false);
    expect(isAiConfigured(config("custom", { apiKey: "", baseUrl: "http://localhost:11434/v1", model: "gemma3" }))).toBe(
      true,
    );
  });

  it("maps HTTP errors to what the user can do", () => {
    expect(aiErrorKind(400, '{"error":{"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}')).toBe(
      "invalid_key",
    );
    expect(aiErrorKind(400, '{"error":{"message":"Invalid image"}}')).toBe("provider");
    expect(aiErrorKind(401, "")).toBe("invalid_key");
    expect(aiErrorKind(403, "")).toBe("invalid_key");
    expect(aiErrorKind(429, "")).toBe("quota");
    expect(aiErrorKind(402, "")).toBe("quota");
    expect(aiErrorKind(404, "")).toBe("not_found");
    expect(aiErrorKind(500, "")).toBe("provider");
  });
});
