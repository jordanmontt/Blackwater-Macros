import type { AiImage } from "@/lib/core/ai-providers";
import {
  buildMealEstimateSystemPrompt,
  buildMealEstimateUserText,
  parseMealEstimate,
  type MealEstimate,
} from "@/lib/core/ai-schema";
import { AiError, aiComplete } from "@/lib/ai/client";
import { aiConfigOf, getAiSettings } from "@/lib/ai/settings";

/** The web app is in Spanish; the model is told in English which language to answer in. */
export const AI_LANGUAGE = "Spanish";

/**
 * Photos and/or a description → a meal estimate for the review form. The
 * photos go only to the provider chosen in Ajustes and are then dropped.
 */
export async function estimateMeal(
  input: { description: string; photos: AiImage[] },
  signal?: AbortSignal,
): Promise<MealEstimate> {
  const text = await aiComplete(
    aiConfigOf(getAiSettings()),
    {
      system: buildMealEstimateSystemPrompt(AI_LANGUAGE),
      messages: [
        { role: "user", text: buildMealEstimateUserText(input.description, input.photos.length), images: input.photos },
      ],
      json: true,
      stream: false,
      // Thinking models spend part of this before answering.
      maxTokens: 8192,
    },
    signal,
  );
  const result = parseMealEstimate(text);
  if (!result.ok) throw new AiError("unreadable", text.slice(0, 300));
  return result.estimate;
}
