import data from "./food-questions.json";

/**
 * The shared part of food questions, as data: the rules (minimum days, caps),
 * the prompt that turns a question into a request, the analyses' descriptions
 * and the text templates of the result. Android reads the same file.
 */
export const FOOD_QUESTIONS = data;

export const RULES = data.rules;

export type AnalysisId = keyof typeof data.analyses;

export type Metric = keyof typeof data.result.metrics;

export type GroupLabel = keyof typeof data.result.labels;

export type RequestField = keyof typeof data.fields;

/** Every analysis, in the order of the file (the prompt lists them so). */
export const ANALYSIS_IDS = Object.keys(data.analyses) as AnalysisId[];

/** «{name}» in [template] replaced by its value; unknown names are left as they are. */
export function fill(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (whole, name: string) =>
    name in values ? String(values[name]) : whole,
  );
}

/** Same text on every platform: integers without decimals, «80.2», never «80.0» or «-0». */
export function num(value: number): string {
  return String(value === 0 ? 0 : value);
}
