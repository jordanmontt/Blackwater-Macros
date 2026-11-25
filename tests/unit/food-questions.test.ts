import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  ANALYSES,
  FOOD_QUESTIONS,
  answerFoodRequest,
  buildFoodRequestPrompt,
  foodQuestionMessage,
  mostLoggedFood,
  parseFoodRequest,
  requestJson,
  type FoodAnswer,
  type FoodLog,
  type FoodRequest,
} from "../../src/lib/core/food-questions";
import type { Amount } from "../../src/lib/core/food-questions/log";
import { resultText } from "../../src/lib/core/food-questions/answer";
import { matchesTerms, normalizeName, parseAmount, searchTerms } from "../../src/lib/core/food-questions/log";
import { ANALYSIS_IDS, fill } from "../../src/lib/core/food-questions/spec";

/**
 * The expectations live in tests/fixtures/food-questions/, shared with
 * Android's FoodQuestionsTest.kt: both platforms must give the same answers.
 */
const FIXTURES = resolve(__dirname, "../fixtures/food-questions");
const read = (name: string) => readFileSync(resolve(FIXTURES, name), "utf8");
const LOG = JSON.parse(read("log.json")) as FoodLog;

interface SharedCase {
  name: string;
  /** The model's step-1 answer, as text. */
  model: string;
  /** Null: unreadable. */
  request: FoodRequest | null;
  requestJson: string | null;
  /** Null: «none», or not answerable as asked. */
  answer: FoodAnswer | null;
}

interface Expected {
  basics: {
    amounts: { quantity: string; amount: Amount | null }[];
    names: { name: string; normalized: string }[];
    matches: { name: string; foods: string[]; matches: boolean }[];
    mostLoggedFood: string;
    cut: { lines: string[]; text: string };
  };
  cases: SharedCase[];
}

const EXPECTED = JSON.parse(read("cases.json")) as Expected;

describe("food questions: reading the log", () => {
  it.each(EXPECTED.basics.amounts)("amount of «$quantity»", ({ quantity, amount }) => {
    expect(parseAmount(quantity)).toEqual(amount);
  });

  it.each(EXPECTED.basics.names)("normalizes «$name»", ({ name, normalized }) => {
    expect(normalizeName(name)).toBe(normalized);
  });

  it.each(EXPECTED.basics.matches)("«$name» for $foods", ({ name, foods, matches }) => {
    expect(matchesTerms(normalizeName(name), searchTerms(foods))).toBe(matches);
  });

  it("the food logged on most days in the last 4 weeks, for the example question", () => {
    expect(mostLoggedFood(LOG.meals, LOG.today)).toBe(EXPECTED.basics.mostLoggedFood);
    expect(mostLoggedFood([], LOG.today)).toBeNull();
  });

  it("the result is cut at whole lines to fit the phone model's window", () => {
    const { lines, text } = EXPECTED.basics.cut;
    expect(resultText(lines)).toBe(text);
    expect(text.length).toBeLessThanOrEqual(FOOD_QUESTIONS.rules.resultMaxChars);
  });
});

describe("food questions: the request (step 1)", () => {
  it("the prompt lists every analysis, the fields, examples with today's dates and the previous request", () => {
    const previous = EXPECTED.cases[0].request!;
    expect(buildFoodRequestPrompt(LOG.today, "Spanish", previous)).toBe(read("request-prompt.txt").trimEnd());
  });

  it("without a previous request the prompt ends with the examples", () => {
    expect(buildFoodRequestPrompt(LOG.today, "Spanish", null).endsWith('Request: {"analysis":"none"}')).toBe(true);
  });
});

describe("food questions: shared cases (request → answer)", () => {
  it.each(EXPECTED.cases)("$name", ({ model, request, requestJson: json, answer }) => {
    const parsed = parseFoodRequest(model);
    expect(parsed.ok ? parsed.request : null).toEqual(request);
    if (request === null) return;
    expect(requestJson(request)).toBe(json);
    expect(answerFoodRequest(LOG, request)).toEqual(answer);
  });
});

describe("food questions: the registry", () => {
  it("every analysis in the shared file has its code, and the other way round", () => {
    expect(Object.keys(ANALYSES).sort()).toEqual([...ANALYSIS_IDS].sort());
    for (const id of ANALYSIS_IDS) expect(ANALYSES[id].id).toBe(id);
  });

  it("each analysis's required fields are fields it uses, and every field is described", () => {
    for (const id of ANALYSIS_IDS) {
      const analysis = FOOD_QUESTIONS.analyses[id];
      for (const field of analysis.required) expect(analysis.fields).toContain(field);
      for (const field of analysis.fields) expect(Object.keys(FOOD_QUESTIONS.fields)).toContain(field);
    }
  });

  it("every example request can be read back", () => {
    for (const example of FOOD_QUESTIONS.examples) {
      const text = fill(JSON.stringify(example.request), { yesterday: "2026-02-28", lastMonthFrom: "2026-02-01", lastMonthTo: "2026-02-28" });
      expect(parseFoodRequest(text).ok).toBe(true);
    }
  });

  it("step 3 reads the user's words, then the app's numbers", () => {
    expect(foodQuestionMessage("¿Cuánta leche tomo?", "FOOD LOG RESULT…")).toBe("¿Cuánta leche tomo?\n\nFOOD LOG RESULT…");
  });
});
