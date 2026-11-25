import { extractJson } from "../ai-schema";
import { addDaysToKey, isValidDateKey } from "../dates";
import { weekdayName } from "./log";
import { ANALYSIS_IDS, FOOD_QUESTIONS, RULES, fill, type AnalysisId, type Metric, type RequestField } from "./spec";

export type Split = "weekday_weekend" | "periods";

/**
 * What the model asks the app to compute (step 1). Fields the analysis does
 * not use are null or empty.
 */
export interface FoodRequest {
  analysis: AnalysisId | "none";
  foods: string[];
  metric: Metric | null;
  split: Split | null;
  from: string | null;
  to: string | null;
  from2: string | null;
  to2: string | null;
  at: string | null;
}

export type FoodRequestResult = { ok: true; request: FoodRequest } | { ok: false };

const METRICS = Object.keys(FOOD_QUESTIONS.result.metrics) as Metric[];
const SPLITS: Split[] = ["weekday_weekend", "periods"];
const DATE_FIELDS = ["from", "to", "from2", "to2", "at"] as const;

export const NO_REQUEST: FoodRequest = {
  analysis: "none",
  foods: [],
  metric: null,
  split: null,
  from: null,
  to: null,
  from2: null,
  to2: null,
  at: null,
};

/** A request as compact JSON with only its fields (for the prompt and the conversation). */
export function requestJson(request: FoodRequest): string {
  const value: Record<string, unknown> = { analysis: request.analysis };
  if (request.foods.length > 0) value.foods = request.foods;
  if (request.metric !== null) value.metric = request.metric;
  if (request.split !== null) value.split = request.split;
  for (const field of DATE_FIELDS) if (request[field] !== null) value[field] = request[field];
  return JSON.stringify(value);
}

/**
 * The system prompt of step 1: the question becomes a request. [previous] is
 * the last request of the conversation, for follow-ups («¿y el mes pasado?»).
 */
export function buildFoodRequestPrompt(today: string, language: string, previous: FoodRequest | null): string {
  const firstOfMonth = `${today.slice(0, 8)}01`;
  const lastMonthTo = addDaysToKey(firstOfMonth, -1);
  const dates = {
    yesterday: addDaysToKey(today, -1),
    lastMonthFrom: `${lastMonthTo.slice(0, 8)}01`,
    lastMonthTo,
  };
  const analyses = ANALYSIS_IDS.map((id) => {
    const analysis = FOOD_QUESTIONS.analyses[id];
    return `- ${id}: ${analysis.description}. Fields: ${analysis.fields.join(", ")}.`;
  }).concat(`- none: ${FOOD_QUESTIONS.noneDescription}.`);
  const fields = (Object.keys(FOOD_QUESTIONS.fields) as RequestField[]).map(
    (field) => `- ${field}: ${fill(FOOD_QUESTIONS.fields[field], { language })}`,
  );
  const examples = FOOD_QUESTIONS.examples.map(
    (example) => `Question: ${example.question}\nRequest: ${fill(JSON.stringify(example.request), dates)}`,
  );
  const prompt = fill(FOOD_QUESTIONS.requestPrompt.join("\n"), {
    today,
    weekday: weekdayName(today),
    language,
    analyses: analyses.join("\n"),
    fields: fields.join("\n"),
    examples: examples.join("\n"),
    previous: previous === null ? "" : fill(FOOD_QUESTIONS.previousRequest, { request: requestJson(previous) }),
  });
  return prompt.trimEnd();
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

/** Missing (or null) → null; a valid day → it; anything else → undefined (unreadable). */
function dateField(value: unknown): string | null | undefined {
  if (value === undefined || value === null || value === "") return null;
  return typeof value === "string" && isValidDateKey(value.trim()) ? value.trim() : undefined;
}

function enumField<T extends string>(value: unknown, allowed: T[]): T | null | undefined {
  if (value === undefined || value === null || value === "") return null;
  return typeof value === "string" && (allowed as string[]).includes(value) ? (value as T) : undefined;
}

function foodsField(value: unknown): string[] | undefined {
  if (value === undefined || value === null) return [];
  const list = typeof value === "string" ? [value] : value;
  if (!Array.isArray(list) || list.some((food) => typeof food !== "string")) return undefined;
  return (list as string[])
    .map((food) => food.trim())
    .filter((food) => food !== "")
    .slice(0, RULES.maxSearchWords);
}

/**
 * The model's answer to step 1 as a request, or `ok: false` when it cannot be
 * read: no JSON, an unknown analysis, a value of the wrong kind, a required
 * field missing or a period that ends before it starts.
 */
export function parseFoodRequest(text: string): FoodRequestResult {
  const json = asRecord(extractJson(text));
  if (!json) return { ok: false };
  const analysis = json.analysis;
  if (analysis === "none") return { ok: true, request: NO_REQUEST };
  if (typeof analysis !== "string" || !(ANALYSIS_IDS as string[]).includes(analysis)) return { ok: false };
  const id = analysis as AnalysisId;
  const spec = FOOD_QUESTIONS.analyses[id];
  const uses = (field: RequestField) => (spec.fields as string[]).includes(field);

  const foods = foodsField(json.foods);
  const metric = enumField(json.metric, METRICS);
  const split = enumField(json.split, SPLITS);
  const dates = DATE_FIELDS.map((field) => dateField(json[field]));
  if (foods === undefined || metric === undefined || split === undefined || dates.includes(undefined)) return { ok: false };
  const [from, to, from2, to2, at] = dates as (string | null)[];

  const request: FoodRequest = {
    analysis: id,
    foods: uses("foods") ? foods : [],
    metric: uses("metric") ? metric : null,
    split: uses("split") ? split : null,
    from: uses("from") ? from : null,
    to: uses("to") ? to : null,
    from2: uses("from2") ? from2 : null,
    to2: uses("to2") ? to2 : null,
    at: uses("at") ? at : null,
  };
  for (const field of spec.required as RequestField[]) {
    const value = request[field];
    if (value === null || (Array.isArray(value) && value.length === 0)) return { ok: false };
  }
  if (request.from !== null && request.to !== null && request.from > request.to) return { ok: false };
  if (request.from2 !== null && request.to2 !== null && request.from2 > request.to2) return { ok: false };
  return { ok: true, request };
}
