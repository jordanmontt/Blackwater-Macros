import { compare } from "./analyses/compare";
import { dayLog } from "./analyses/day-log";
import { foodStats } from "./analyses/food-stats";
import { foodVsWeight } from "./analyses/food-vs-weight";
import { ranking } from "./analyses/ranking";
import { prepareLog, type Analysis, type FoodAnswer } from "./answer";
import type { FoodLog } from "./log";
import type { FoodRequest } from "./request";
import type { AnalysisId } from "./spec";

/**
 * Food questions: the coach answers questions about the user's own food log
 * («¿Cuánta leche chocolatada tomo?») in three steps. (1) The model turns the
 * question into a request (`buildFoodRequestPrompt` → `parseFoodRequest`);
 * (2) the app computes the numbers from the log (`answerFoodRequest`); (3) the
 * model explains the result, which travels with the question
 * (`foodQuestionMessage`). The model never computes a number.
 *
 * Adding an analysis: its texts in food-questions.json, one file in
 * analyses/, one line in [ANALYSES] (docs/TECHNICAL.md §8.7).
 */
export const ANALYSES: Record<AnalysisId, Analysis> = {
  food_stats: foodStats,
  ranking,
  day_log: dayLog,
  compare,
  food_vs_weight: foodVsWeight,
};

/**
 * What the app answers to a request: numbers for the model to explain, or a
 * reply of its own. Null when the request cannot be answered as asked; the
 * coach then says it did not understand (as for an unreadable request).
 */
export function answerFoodRequest(log: FoodLog, request: FoodRequest): FoodAnswer | null {
  if (request.analysis === "none") return null;
  return ANALYSES[request.analysis].answer(prepareLog(log), request);
}

/** What the model reads in step 3: the user's words, then the app's numbers. */
export function foodQuestionMessage(question: string, resultText: string): string {
  return `${question}\n\n${resultText}`;
}

export type { FoodAnswer, FoodDetails, FoodGroup, NotEnoughReason, SplitReason } from "./answer";
export type { FoodLog, FoodLogMeal } from "./log";
export { mostLoggedFood } from "./log";
export type { FoodRequest, FoodRequestResult } from "./request";
export { buildFoodRequestPrompt, parseFoodRequest, requestJson, NO_REQUEST } from "./request";
export { FOOD_QUESTIONS, RULES as FOOD_QUESTION_RULES } from "./spec";
export type { AnalysisId, GroupLabel, Metric } from "./spec";
