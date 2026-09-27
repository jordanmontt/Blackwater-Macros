import { round1 } from "./nutrition";
import type { IngredientInput } from "./types";

export type EstimateConfidence = "low" | "medium" | "high";

export interface EstimatedItem {
  name: string;
  grams: number | null;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
  /** kcal differ by more than 25 % from 4·protein + 4·carbs + 9·fat: worth a second look. */
  inconsistent: boolean;
}

export interface MealEstimate {
  title: string;
  items: EstimatedItem[];
  confidence: EstimateConfidence | null;
  notes: string | null;
}

export type MealEstimateResult =
  | { ok: true; estimate: MealEstimate }
  | { ok: false; error: "no_json" | "no_items" };

/**
 * The JSON the AI is asked to return (put in the prompt; Gemini also gets it as
 * a response schema). Keys are English on purpose: models follow them best.
 */
export const MEAL_ESTIMATE_SHAPE =
  '{"title": string, "items": [{"name": string, "grams": number, "calories": number, ' +
  '"protein": number, "carbs": number, "fat": number}], "confidence": "low" | "medium" | "high", "notes": string}';

/**
 * The first JSON value in a model answer, tolerating ```json fences and text
 * around it. Null when there is none that parses.
 */
export function extractJson(text: string): unknown {
  // An object or a list, whichever opens first; the other one as a fallback.
  const candidates = [
    [text.indexOf("{"), text.lastIndexOf("}")],
    [text.indexOf("["), text.lastIndexOf("]")],
  ]
    .filter(([start, end]) => start >= 0 && end > start)
    .sort((a, b) => a[0] - b[0]);
  for (const [start, end] of candidates) {
    try {
      return JSON.parse(text.slice(start, end + 1));
    } catch {
      // try the next candidate
    }
  }
  return null;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function toText(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

/** Numbers, or the first number in a string («150 g» → 150). */
function toNumber(value: unknown): number | null {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "string") {
    const match = /-?\d+(?:[.,]\d+)?/.exec(value);
    return match ? Number(match[0].replace(",", ".")) : null;
  }
  return null;
}

function firstNumber(record: Record<string, unknown>, keys: string[]): number | null {
  for (const key of keys) {
    const value = toNumber(record[key]);
    if (value !== null) return value;
  }
  return null;
}

function parseItem(value: unknown): EstimatedItem | null {
  const record = asRecord(value);
  if (!record) return null;
  const name = toText(record.name) ?? toText(record.food);
  if (!name) return null;

  const nonNegative = (n: number | null) => Math.max(n ?? 0, 0);
  const grams = firstNumber(record, ["grams", "weight_g", "quantity_g", "amount_g", "weight", "quantity"]);
  const calories = Math.round(nonNegative(firstNumber(record, ["calories", "kcal", "energy_kcal", "energy"])));
  const protein = round1(nonNegative(firstNumber(record, ["protein", "proteins", "protein_g"])));
  const carbs = round1(nonNegative(firstNumber(record, ["carbs", "carbohydrates", "carbs_g", "carbohydrates_g"])));
  const fat = round1(nonNegative(firstNumber(record, ["fat", "fats", "fat_g"])));

  const fromMacros = 4 * protein + 4 * carbs + 9 * fat;
  const larger = Math.max(calories, fromMacros);
  const inconsistent = larger >= 20 && Math.abs(calories - fromMacros) > 0.25 * larger;

  return {
    name,
    grams: grams !== null && grams > 0 ? Math.round(grams) : null,
    calories,
    protein,
    carbs,
    fat,
    inconsistent,
  };
}

/**
 * Turns a model answer into a meal estimate. Accepts an object with `items`
 * (or `ingredients` / `foods`) or a bare list of items; unknown or negative
 * numbers become 0; items without a name are dropped.
 */
export function parseMealEstimate(text: string): MealEstimateResult {
  const json = extractJson(text);
  if (json === null) return { ok: false, error: "no_json" };

  const record = asRecord(json);
  const list = Array.isArray(json)
    ? json
    : record
      ? [record.items, record.ingredients, record.foods].find(Array.isArray) ?? []
      : [];
  const items = (list as unknown[]).map(parseItem).filter((item): item is EstimatedItem => item !== null);
  if (items.length === 0) return { ok: false, error: "no_items" };

  const confidenceText = toText(record?.confidence)?.toLowerCase();
  const confidence =
    confidenceText === "low" || confidenceText === "medium" || confidenceText === "high" ? confidenceText : null;

  return {
    ok: true,
    estimate: {
      title: toText(record?.title) ?? items.slice(0, 3).map((item) => item.name).join(", "),
      items,
      confidence,
      notes: toText(record?.notes),
    },
  };
}

/** Ingredient rows for the meal form (per-ingredient mode). */
export function estimateToIngredients(estimate: MealEstimate): IngredientInput[] {
  return estimate.items.map((item) => ({
    name: item.name,
    quantity: item.grams === null ? undefined : `${item.grams} g`,
    calories: item.calories,
    protein: item.protein,
    carbs: item.carbs,
    fat: item.fat,
  }));
}

/** System prompt for estimating a meal from photos and/or a description. `language` in English («Spanish»). */
export function buildMealEstimateSystemPrompt(language: string): string {
  return [
    "You estimate the nutrition of meals for Blackwater Macros, a calorie and macro tracker.",
    `Reply with only one JSON object with this shape: ${MEAL_ESTIMATE_SHAPE}`,
    `Write the title, the item names and the notes in ${language}.`,
    "One item per food. Add likely hidden ingredients (cooking oil, butter, sauces, dressings, sugar) as their own items.",
    "grams is the edible weight as served. Judge portions with the references in the photos (plate, cutlery, hand).",
    "Quantities the user gives and nutrition labels in the photos beat visual guesses.",
    "calories must match 4 x protein + 4 x carbs + 9 x fat. Whole numbers for grams and calories, one decimal for macros.",
    "confidence is high only when every portion is clear, low when the photo is unclear or much is hidden.",
    `notes is one short sentence in ${language} with the main assumption (for example the amount of oil).`,
  ].join("\n");
}

/** The user message next to the photos: how many there are and what the user wrote. */
export function buildMealEstimateUserText(description: string, photoCount: number): string {
  const text = description.trim();
  if (photoCount === 0) return `Estimate this meal: ${text}`;
  const photos =
    photoCount === 1
      ? "A photo of my meal."
      : `${photoCount} photos of the same meal from different angles (one may be a nutrition label).`;
  return text === "" ? photos : `${photos}\nWhat I can add: ${text}`;
}
