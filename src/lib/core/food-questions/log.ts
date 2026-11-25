import { addDaysToKey, daysBetweenKeys } from "../dates";
import type { CoachWeight } from "../coach";
import type { EntryMode, IngredientInput } from "../types";
import { RULES } from "./spec";

/** A meal as food questions read it (the coach's meal plus how it was entered). */
export interface FoodLogMeal {
  logDate: string;
  title: string;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  resolvedCalories: number;
  resolvedProtein: number;
  resolvedCarbs: number;
  resolvedFat: number;
}

/** Everything an answer may read: the meals up to today and the weigh-ins, oldest first. */
export interface FoodLog {
  today: string;
  meals: FoodLogMeal[];
  weights: CoachWeight[];
}

export type AmountUnit = "g" | "ml";

export interface Amount {
  value: number;
  unit: AmountUnit;
}

/**
 * One food as logged: an ingredient, or a «Solo total» meal by its title (its
 * totals are the meal's). `key` is the name for matching and grouping.
 */
export interface FoodItem {
  date: string;
  name: string;
  key: string;
  amount: Amount | null;
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
}

export interface DayTotals {
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
}

export interface DayRange {
  from: string;
  to: string;
}

/** Lower case, without accents, words split on anything that is not a letter or digit. */
export function normalizeName(name: string): string {
  return name
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim();
}

/** Unit as written → the unit it is counted in and the factor to it. */
const UNIT_FACTORS: Record<string, [AmountUnit, number]> = {
  g: ["g", 1],
  gr: ["g", 1],
  grs: ["g", 1],
  gramo: ["g", 1],
  gramos: ["g", 1],
  gram: ["g", 1],
  grams: ["g", 1],
  kg: ["g", 1000],
  ml: ["ml", 1],
  cl: ["ml", 10],
  dl: ["ml", 100],
  l: ["ml", 1000],
  lt: ["ml", 1000],
  litro: ["ml", 1000],
  litros: ["ml", 1000],
  liter: ["ml", 1000],
  liters: ["ml", 1000],
  litre: ["ml", 1000],
  litres: ["ml", 1000],
};

const AMOUNT_RE = /([0-9]+(?:[.,][0-9]+)?)[ \t\u00a0]*(kg|grams|gram|gramos|gramo|grs|gr|g|ml|cl|dl|litres|litre|liters|liter|litros|litro|lt|l)(?![\p{L}])/iu;
const NUMBER_RE = /[0-9]+(?:[.,][0-9]+)?/g;

/**
 * The amount in g or ml of a free-text quantity («200 ml», «1,5 kg», «150g»).
 * Only when it is the one number there: «1 taza (250 ml)» or «2 x 30 g» are
 * left out rather than guessed.
 */
export function parseAmount(quantity: string | undefined): Amount | null {
  if (!quantity) return null;
  if ((quantity.match(NUMBER_RE) ?? []).length !== 1) return null;
  const match = AMOUNT_RE.exec(quantity);
  if (!match) return null;
  const [unit, factor] = UNIT_FACTORS[match[2].toLowerCase()];
  const value = Number(match[1].replace(",", ".")) * factor;
  return value > 0 ? { value, unit } : null;
}

/** The foods of the meals, in their order. */
export function foodItems(meals: FoodLogMeal[]): FoodItem[] {
  const items: FoodItem[] = [];
  for (const meal of meals) {
    if (meal.entryMode === "total_only") {
      const name = meal.title.trim();
      if (name === "") continue;
      items.push({
        date: meal.logDate,
        name,
        key: normalizeName(name),
        amount: null,
        calories: meal.resolvedCalories,
        protein: meal.resolvedProtein,
        carbs: meal.resolvedCarbs,
        fat: meal.resolvedFat,
      });
      continue;
    }
    for (const ingredient of meal.ingredients) {
      const name = ingredient.name.trim();
      if (name === "") continue;
      items.push({
        date: meal.logDate,
        name,
        key: normalizeName(name),
        amount: parseAmount(ingredient.quantity),
        calories: ingredient.calories ?? 0,
        protein: ingredient.protein ?? 0,
        carbs: ingredient.carbs ?? 0,
        fat: ingredient.fat ?? 0,
      });
    }
  }
  return items;
}

/** Each day's totals, from the meals' resolved totals. */
export function dayTotals(meals: FoodLogMeal[]): Map<string, DayTotals> {
  const totals = new Map<string, DayTotals>();
  for (const meal of meals) {
    const day = totals.get(meal.logDate) ?? { calories: 0, protein: 0, carbs: 0, fat: 0 };
    day.calories += meal.resolvedCalories;
    day.protein += meal.resolvedProtein;
    day.carbs += meal.resolvedCarbs;
    day.fat += meal.resolvedFat;
    totals.set(meal.logDate, day);
  }
  return totals;
}

/** The search words, as lists of normalized words (empty ones dropped, at most `maxSearchWords`). */
export function searchTerms(foods: string[]): string[][] {
  return foods
    .map((food) => normalizeName(food).split(" ").filter((word) => word !== ""))
    .filter((words) => words.length > 0)
    .slice(0, RULES.maxSearchWords);
}

/** A name matches a search term when every word of the term starts a word of the name. */
export function matchesTerms(key: string, terms: string[][]): boolean {
  const words = key.split(" ");
  return terms.some((term) => term.every((part) => words.some((word) => word.startsWith(part))));
}

/** Code-unit order, the same in TypeScript and Kotlin. */
export function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/** The days from `from` to `to`, both included. */
export function daysOf(range: DayRange): string[] {
  const days: string[] = [];
  for (let day = range.from; day <= range.to; day = addDaysToKey(day, 1)) days.push(day);
  return days;
}

export function inRange(date: string, range: DayRange): boolean {
  return date >= range.from && date <= range.to;
}

export function rangeDays(range: DayRange): number {
  return daysBetweenKeys(range.from, range.to) + 1;
}

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

/** «Monday»: the model reads English. */
export function weekdayName(date: string): string {
  return WEEKDAYS[new Date(`${date}T00:00:00`).getDay()];
}

export function isWeekend(date: string): boolean {
  const day = new Date(`${date}T00:00:00`).getDay();
  return day === 0 || day === 6;
}

/**
 * The period a request asks for: `to` never after today; without `from`, the
 * `defaultDays` up to `to`. Null when it starts after it ends.
 */
export function resolveRange(from: string | null, to: string | null, today: string, defaultDays: number): DayRange | null {
  const end = to === null || to > today ? today : to;
  const start = from ?? addDaysToKey(end, -(defaultDays - 1));
  return start <= end ? { from: start, to: end } : null;
}

/** Each name as it was written most often, by normalized name. */
export function displayNames(items: FoodItem[]): Map<string, string> {
  const counts = new Map<string, Map<string, number>>();
  for (const item of items) {
    const spellings = counts.get(item.key) ?? new Map<string, number>();
    spellings.set(item.name, (spellings.get(item.name) ?? 0) + 1);
    counts.set(item.key, spellings);
  }
  const names = new Map<string, string>();
  for (const [key, spellings] of counts) {
    const best = [...spellings].sort((a, b) => b[1] - a[1] || compareText(a[0], b[0]))[0][0];
    names.set(key, best);
  }
  return names;
}

/** The names of [items], the most frequent (by days) first. */
export function namesByDays(items: FoodItem[]): string[] {
  const days = new Map<string, Set<string>>();
  for (const item of items) {
    const dates = days.get(item.key) ?? new Set<string>();
    dates.add(item.date);
    days.set(item.key, dates);
  }
  const names = displayNames(items);
  return [...days]
    .map(([key, dates]) => ({ name: names.get(key)!, days: dates.size }))
    .sort((a, b) => b.days - a.days || compareText(a.name, b.name))
    .map((entry) => entry.name);
}

/** The food logged on most days in the 28 days up to today, for example questions; null with none. */
export function mostLoggedFood(meals: FoodLogMeal[], today: string): string | null {
  const range = { from: addDaysToKey(today, -(RULES.defaultDays - 1)), to: today };
  return namesByDays(foodItems(meals.filter((meal) => inRange(meal.logDate, range))))[0] ?? null;
}
