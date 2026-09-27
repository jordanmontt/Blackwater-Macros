import { round1, type NutritionTotals } from "./nutrition";
import type { IngredientInput } from "./types";

/** Nutrition per 100 g (or 100 ml) of a food, as food databases publish it. */
export interface Per100g {
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
}

/** A product from Open Food Facts (barcode or text search). */
export interface FoodProduct {
  code: string | null;
  name: string;
  brand: string | null;
  per100g: Per100g;
  /** Grams (or ml) in one serving, when the product declares it. */
  servingGrams: number | null;
  /** Some macros were missing in the source and count as 0. */
  incomplete: boolean;
}

export type FoodLang = "es" | "en" | "fr" | "de" | "it";

/** A generic food from the bundled offline index (USDA, CIQUAL, Swiss FCDB). */
export interface GenericFood {
  id: string;
  source: string;
  names: Partial<Record<FoodLang, string>>;
  per100g: Per100g;
}

export interface GenericFoodMatch {
  food: GenericFood;
  /** Name to show: the one in the app language when there is one. */
  name: string;
}

/** Nutrition of `grams` of a food: kcal rounded to integers, macros to 1 decimal. */
export function scalePer100g(per100g: Per100g, grams: number): NutritionTotals {
  const factor = grams / 100;
  return {
    calories: Math.round(per100g.calories * factor),
    protein: round1(per100g.protein * factor),
    carbs: round1(per100g.carbs * factor),
    fat: round1(per100g.fat * factor),
  };
}

/** An ingredient row for the meal form: «Yogur griego · 150 g» with its macros. */
export function foodToIngredient(name: string, per100g: Per100g, grams: number): IngredientInput {
  return { name, quantity: `${round1(grams)} g`, ...scalePer100g(per100g, grams) };
}

/**
 * Grams in a serving description: «30 g» → 30, «1 cup (240 ml)» → 240,
 * «2 galletas (25g)» → 25. Millilitres count as grams. Null when there is none.
 */
export function parseServingGrams(text: string | null | undefined): number | null {
  if (!text) return null;
  const match = /(\d+(?:[.,]\d+)?)\s*(?:g|gr|grams?|gramos?|ml)(?![a-z])/i.exec(text);
  if (!match) return null;
  const grams = Number(match[1].replace(",", "."));
  return grams > 0 ? grams : null;
}

/** Lowercase, without accents and with single spaces: «Yogúr  Griego» → «yogur griego». */
export function normalizeText(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{Mn}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function toNumber(value: unknown): number | null {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value === "string") {
    const match = /-?\d+(?:[.,]\d+)?/.exec(value);
    return match ? Number(match[0].replace(",", ".")) : null;
  }
  return null;
}

function toText(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

const KJ_PER_KCAL = 4.184;

/**
 * One Open Food Facts product object (from `/api/v2/product/<code>` or a
 * Search-a-licious hit). Null when it has no energy value: without kcal it is
 * useless for this app. With `lang`, `product_name_<lang>` wins over the
 * generic name (Spanish first).
 */
export function parseOffProductFields(value: unknown, lang: FoodLang | null = null): FoodProduct | null {
  const product = asRecord(value);
  if (!product) return null;
  const nutriments = asRecord(product.nutriments) ?? {};

  const kcal = toNumber(nutriments["energy-kcal_100g"]);
  const kj = toNumber(nutriments["energy-kj_100g"]) ?? toNumber(nutriments["energy_100g"]);
  const calories = kcal ?? (kj === null ? null : kj / KJ_PER_KCAL);
  if (calories === null || calories < 0) return null;

  const protein = toNumber(nutriments["proteins_100g"]);
  const carbs = toNumber(nutriments["carbohydrates_100g"]);
  const fat = toNumber(nutriments["fat_100g"]);

  const brands = Array.isArray(product.brands) ? toText(product.brands[0]) : toText(product.brands);
  const brand = brands ? brands.split(",")[0].trim() : null;
  const code = toText(product.code);
  const localized = lang ? toText(product[`product_name_${lang}`]) : null;
  const name = localized ?? toText(product.product_name) ?? toText(product.generic_name) ?? brand ?? code ?? "";

  const declaredServing = toNumber(product.serving_quantity);
  const servingGrams =
    declaredServing !== null && declaredServing > 0
      ? declaredServing
      : parseServingGrams(toText(product.serving_size));

  return {
    code,
    name,
    brand,
    per100g: {
      calories: round1(calories),
      protein: round1(Math.max(protein ?? 0, 0)),
      carbs: round1(Math.max(carbs ?? 0, 0)),
      fat: round1(Math.max(fat ?? 0, 0)),
    },
    servingGrams,
    incomplete: protein === null || carbs === null || fat === null,
  };
}

/** Response of `/api/v2/product/<code>.json`; null when the product is unknown. */
export function parseOffProduct(json: unknown, lang: FoodLang | null = null): FoodProduct | null {
  const response = asRecord(json);
  if (!response || response.status === 0) return null;
  return parseOffProductFields(response.product, lang);
}

/** Search-a-licious (`hits`) or legacy search (`products`) response. */
export function parseOffSearch(json: unknown, lang: FoodLang | null = null): FoodProduct[] {
  const response = asRecord(json);
  if (!response) return [];
  const list = Array.isArray(response.hits) ? response.hits : Array.isArray(response.products) ? response.products : [];
  return list.map((item) => parseOffProductFields(item, lang)).filter((p): p is FoodProduct => p !== null);
}

const WORD_SPLIT = /[^a-z0-9]+/;

/**
 * Words that mean the same food in Spain and Latin America (normalized: no
 * accents, lowercase). The bundled names use the Spain variant; a query with
 * any word of a group also matches the others.
 */
export const SPANISH_SYNONYMS: string[][] = [
  ["platano", "banana", "banano", "cambur"],
  ["patata", "papa"],
  ["alubia", "judia", "frijol", "poroto", "habichuela"],
  ["melocoton", "durazno"],
  ["zumo", "jugo"],
  ["maiz", "choclo", "elote"],
  ["gamba", "camaron", "langostino"],
  ["cacahuete", "cacahuate", "mani"],
  ["aguacate", "palta"],
  ["fresa", "frutilla"],
  ["guisante", "arveja", "chicharo"],
  ["calabacin", "zapallito", "calabacita"],
  ["pimiento", "morron"],
  ["albaricoque", "damasco", "chabacano"],
  ["pina", "anana"],
  ["bacon", "beicon", "tocino", "tocineta"],
  ["boniato", "batata", "camote"],
  ["remolacha", "betabel", "betarraga"],
  ["col", "repollo"],
  ["cerdo", "puerco", "chancho"],
  ["ternera", "res", "vacuno", "vaca"],
  ["magdalena", "muffin"],
  ["pomelo", "toronja"],
  ["sandia", "patilla"],
  ["champinon", "hongo", "seta"],
  ["tomate", "jitomate"],
  ["refresco", "gaseosa", "soda"],
  ["yogur", "yogurt", "yoghurt"],
  ["galleta", "galletita"],
  ["yuca", "mandioca"],
  ["pavo", "guajolote"],
  ["aceituna", "oliva"],
  ["cereza", "guinda"],
  ["nata", "crema"],
];

const SYNONYMS_BY_WORD = new Map<string, string[]>();
for (const group of SPANISH_SYNONYMS) for (const word of group) SYNONYMS_BY_WORD.set(word, group);

/**
 * What a query word may match: itself, its singular («fresas» → «fresa»,
 * «limones» → «limon») and the synonyms of either.
 */
export function queryAlternatives(token: string): string[] {
  const forms = [token];
  if (token.length >= 5 && token.endsWith("es")) forms.push(token.slice(0, -2));
  if (token.length >= 4 && token.endsWith("s")) forms.push(token.slice(0, -1));
  const result = new Set<string>();
  for (const form of forms) {
    result.add(form);
    for (const synonym of SYNONYMS_BY_WORD.get(form) ?? []) result.add(synonym);
  }
  return [...result];
}


/**
 * Searches the bundled generic foods in every language they have, ignoring case
 * and accents. Every word of the query (or its singular or a Spanish synonym,
 * see `queryAlternatives`) must start a word of the name. Best first: exact
 * name, then names that start with the query, then the rest; within each,
 * names where every query word is a whole word («papas» → «Patata», not
 * «Papaya»), then names in the app language, then shorter names.
 */
export function searchGenericFoods(
  foods: GenericFood[],
  query: string,
  lang: FoodLang,
  limit = 20,
): GenericFoodMatch[] {
  const normalizedQuery = normalizeText(query);
  const tokens = normalizedQuery.split(WORD_SPLIT).filter(Boolean).map(queryAlternatives);
  if (tokens.length === 0) return [];

  const scored: { match: GenericFoodMatch; score: number; length: number }[] = [];
  for (const food of foods) {
    let best: number | null = null;
    for (const [nameLang, name] of Object.entries(food.names)) {
      if (!name) continue;
      const normalizedName = normalizeText(name);
      const words = normalizedName.split(WORD_SPLIT).filter(Boolean);
      if (!tokens.every((alternatives) => words.some((word) => alternatives.some((alt) => word.startsWith(alt))))) continue;
      const base = normalizedName === normalizedQuery ? 0 : normalizedName.startsWith(normalizedQuery) ? 1 : 2;
      const wholeWords = tokens.every((alternatives) => words.some((word) => alternatives.includes(word)));
      const score = base * 4 + (wholeWords ? 0 : 2) + (nameLang === lang ? 0 : 1);
      if (best === null || score < best) best = score;
    }
    if (best === null) continue;
    const display = food.names[lang] ?? food.names.en ?? Object.values(food.names).find(Boolean) ?? food.id;
    scored.push({ match: { food, name: display }, score: best, length: display.length });
  }

  scored.sort(
    (a, b) =>
      a.score - b.score ||
      a.length - b.length ||
      (a.match.food.id < b.match.food.id ? -1 : a.match.food.id > b.match.food.id ? 1 : 0),
  );
  return scored.slice(0, limit).map((entry) => entry.match);
}

/**
 * The bundled index (`public/foods/generic.json`, Android `assets/foods/generic.json`,
 * built by `scripts/foods/build_generic_index.py`):
 * `{ foods: [{ id, s: source, n: { es, en, fr, … }, v: [kcal, protein, carbs, fat] }] }`.
 */
export function parseGenericIndex(json: unknown): GenericFood[] {
  const index = asRecord(json);
  if (!index || !Array.isArray(index.foods)) return [];
  const foods: GenericFood[] = [];
  for (const entry of index.foods) {
    const food = asRecord(entry);
    const names = asRecord(food?.n);
    const values = food?.v;
    if (!food || !names || !Array.isArray(values) || values.length < 4) continue;
    const [calories, protein, carbs, fat] = values.map((value) => toNumber(value) ?? 0);
    const parsedNames: Partial<Record<FoodLang, string>> = {};
    for (const [lang, name] of Object.entries(names)) {
      const text = toText(name);
      if (text && (lang === "es" || lang === "en" || lang === "fr" || lang === "de" || lang === "it")) parsedNames[lang] = text;
    }
    if (Object.keys(parsedNames).length === 0) continue;
    foods.push({ id: String(food.id), source: String(food.s ?? ""), names: parsedNames, per100g: { calories, protein, carbs, fat } });
  }
  return foods;
}

