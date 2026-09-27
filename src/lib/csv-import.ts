import { isValidDateKey } from "@/lib/core/dates";
import type { EntryMode, IngredientInput } from "@/lib/core/types";

/**
 * Reads the CSV files that the export writes (web `export-service.ts`, Android
 * `CsvBackup`), so a backup moves between the web and the phone. Same rules as
 * Android's `CsvBackup.parse`: the header tells the kind, consecutive rows with
 * the same day/title/mode/notes are one meal, rows the server would reject are
 * counted and skipped. Column names are a file format: they stay in Spanish.
 */

export interface ImportedMeal {
  logDate: string;
  title: string;
  notes: string | null;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  totalCalories: number | null;
  totalProtein: number | null;
  totalCarbs: number | null;
  totalFat: number | null;
}

export interface ImportedWeight {
  measuredAt: string;
  weightKg: number;
  bodyFatPct: number | null;
  note: string | null;
}

export type ParsedBackup =
  | { kind: "meals"; meals: ImportedMeal[]; invalidRows: number }
  | { kind: "weights"; weights: ImportedWeight[]; invalidRows: number }
  | { kind: "unknown" };

export const MEAL_HEADER = [
  "fecha",
  "comida",
  "modo",
  "notas",
  "ingrediente",
  "cantidad",
  "kcal_ingrediente",
  "proteina_ingrediente_g",
  "carbohidratos_ingrediente_g",
  "grasa_ingrediente_g",
  "total_kcal_comida",
  "total_proteina_comida_g",
  "total_carbohidratos_comida_g",
  "total_grasa_comida_g",
];
export const WEIGHT_HEADER = ["fecha_hora", "peso_kg", "grasa_corporal_pct", "nota"];

/** RFC-4180 reader: quoted cells, `""` escapes, CRLF or LF, optional UTF-8 BOM. */
export function parseCsvRows(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let inQuotes = false;
  for (let i = text.startsWith("﻿") ? 1 : 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') {
        inQuotes = false;
      } else {
        cell += c;
      }
    } else if (c === '"') {
      inQuotes = true;
    } else if (c === ",") {
      row.push(cell);
      cell = "";
    } else if (c === "\n") {
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else if (c !== "\r") {
      cell += c;
    }
  }
  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

/** Accepts `.` (export format) and `,` (spreadsheets in a Spanish locale). */
function number(cell: string | undefined): number | null {
  const text = (cell ?? "").trim().replace(",", ".");
  if (text === "") return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
}

/** ISO with a zone as is; a local «YYYY-MM-DDTHH:mm[:ss]» is read in this device's zone. */
function normalizeMeasuredAt(value: string): string | null {
  const date = new Date(value.trim());
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

// Same limits as the server's zod schemas (src/server/validation.ts).
function isValidMeal(meal: ImportedMeal): boolean {
  return (
    isValidDateKey(meal.logDate) &&
    meal.title.length > 0 &&
    meal.title.length <= 120 &&
    meal.ingredients.length <= 100 &&
    meal.ingredients.every((ingredient) => ingredient.name.length <= 200)
  );
}

function isValidWeight(weight: ImportedWeight): boolean {
  return (
    weight.weightKg >= 20 &&
    weight.weightKg <= 400 &&
    (weight.bodyFatPct === null || (weight.bodyFatPct >= 3 && weight.bodyFatPct <= 60))
  );
}

function parseMeals(rows: string[][]): ParsedBackup {
  const meals: ImportedMeal[] = [];
  let invalid = 0;
  let current: ImportedMeal | null = null;
  let currentKey: string | null = null;

  const flush = () => {
    if (current) {
      if (isValidMeal(current)) meals.push(current);
      else invalid++;
    }
    current = null;
    currentKey = null;
  };

  for (const raw of rows) {
    const row = [...raw, ...Array(Math.max(MEAL_HEADER.length - raw.length, 0)).fill("")];
    const key = JSON.stringify(row.slice(0, 4));
    const modeText = row[2].trim();
    if (modeText !== "total_only" && modeText !== "per_ingredient") {
      flush();
      invalid++;
      continue;
    }
    const mode: EntryMode = modeText;
    const name = row[4].trim();
    const ingredient: IngredientInput | null = name
      ? {
          name,
          quantity: row[5].trim() || undefined,
          calories: number(row[6]) ?? undefined,
          protein: number(row[7]) ?? undefined,
          carbs: number(row[8]) ?? undefined,
          fat: number(row[9]) ?? undefined,
        }
      : null;
    if (mode === "per_ingredient" && key === currentKey && ingredient && current) {
      (current as ImportedMeal).ingredients.push(ingredient);
      continue;
    }
    flush();
    currentKey = key;
    const totalOnly = mode === "total_only";
    current = {
      logDate: row[0].trim(),
      title: row[1].trim(),
      notes: row[3].trim() || null,
      entryMode: mode,
      ingredients: !totalOnly && ingredient ? [ingredient] : [],
      totalCalories: totalOnly ? number(row[10]) : null,
      totalProtein: totalOnly ? number(row[11]) : null,
      totalCarbs: totalOnly ? number(row[12]) : null,
      totalFat: totalOnly ? number(row[13]) : null,
    };
  }
  flush();
  return { kind: "meals", meals, invalidRows: invalid };
}

function parseWeights(rows: string[][]): ParsedBackup {
  let invalid = 0;
  const weights: ImportedWeight[] = [];
  for (const row of rows) {
    const measuredAt = normalizeMeasuredAt(row[0] ?? "");
    const weightKg = number(row[1]);
    const weight: ImportedWeight | null =
      measuredAt && weightKg !== null
        ? { measuredAt, weightKg, bodyFatPct: number(row[2]), note: (row[3] ?? "").trim() || null }
        : null;
    if (weight && isValidWeight(weight)) weights.push(weight);
    else invalid++;
  }
  return { kind: "weights", weights, invalidRows: invalid };
}

/** Detects the file kind from its header. */
export function parseBackupCsv(text: string): ParsedBackup {
  const rows = parseCsvRows(text).filter((row) => row.some((cell) => cell.trim() !== ""));
  const header = rows[0]?.map((cell) => cell.trim());
  if (!header) return { kind: "unknown" };
  const starts = (expected: string[]) => expected.every((name, i) => header[i] === name);
  if (starts(MEAL_HEADER)) return parseMeals(rows.slice(1));
  if (starts(WEIGHT_HEADER)) return parseWeights(rows.slice(1));
  return { kind: "unknown" };
}

/**
 * What makes two meals «the same» for an import (Android `importKey`): day,
 * title, mode, ingredients and the resulting kcal and protein. Re-importing a
 * file is harmless.
 */
export function mealImportKey(meal: {
  logDate: string;
  title: string;
  entryMode: EntryMode;
  ingredients: IngredientInput[];
  resolvedCalories: number;
  resolvedProtein: number;
}): string {
  const ingredients = meal.ingredients.map((ingredient) => [
    ingredient.name,
    ingredient.quantity ?? null,
    ingredient.calories ?? null,
    ingredient.protein ?? null,
    ingredient.carbs ?? null,
    ingredient.fat ?? null,
  ]);
  return JSON.stringify([meal.logDate, meal.title, meal.entryMode, ingredients, meal.resolvedCalories, meal.resolvedProtein]);
}

/** Same instant and same kilos (Android `importWeights`). */
export function weightImportKey(weight: { measuredAt: string; weightKg: number }): string {
  return `${new Date(weight.measuredAt).toISOString()}|${weight.weightKg}`;
}
