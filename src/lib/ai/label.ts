import type { AiImage } from "@/lib/core/ai-providers";
import {
  buildLabelReadingSystemPrompt,
  LABEL_READING_USER_TEXT,
  parseLabelReading,
  type LabelReading,
} from "@/lib/core/new-product";
import { AiError, aiComplete } from "@/lib/ai/client";
import { aiLanguage } from "@/lib/ai/estimate";
import { aiConfigOf, getAiSettings } from "@/lib/ai/settings";

/**
 * A photo of a nutrition label → the values for «Nuevo producto». Like «Foto o
 * texto», the photo goes only to the provider chosen in Ajustes.
 */
export async function readLabel(photo: AiImage, signal?: AbortSignal): Promise<LabelReading> {
  const text = await aiComplete(
    aiConfigOf(getAiSettings()),
    {
      system: buildLabelReadingSystemPrompt(aiLanguage()),
      messages: [{ role: "user", text: LABEL_READING_USER_TEXT, images: [photo] }],
      json: true,
      stream: false,
      // Thinking models spend part of this before answering.
      maxTokens: 8192,
    },
    signal,
  );
  const reading = parseLabelReading(text);
  if (!reading) throw new AiError("unreadable", text.slice(0, 300));
  return reading;
}
