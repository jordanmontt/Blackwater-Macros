import { describe, expect, it } from "vitest";
import {
  buildMealEstimateSystemPrompt,
  buildMealEstimateUserText,
  estimateToIngredients,
  extractJson,
  MEAL_ESTIMATE_SHAPE,
  parseMealEstimate,
} from "../../src/lib/core/ai-schema";

const CLEAN = `{"title":"Pasta boloñesa","items":[{"name":"Espaguetis cocidos","grams":180,"calories":284,"protein":10.4,"carbs":55.8,"fat":1.7},{"name":"Salsa boloñesa","grams":120,"calories":156,"protein":9.6,"carbs":6,"fat":10.2}],"confidence":"Medium","notes":"Aceite estimado"}`;

describe("parseMealEstimate", () => {
  it("reads a clean answer", () => {
    const result = parseMealEstimate(CLEAN);
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.estimate).toEqual({
      title: "Pasta boloñesa",
      items: [
        { name: "Espaguetis cocidos", grams: 180, calories: 284, protein: 10.4, carbs: 55.8, fat: 1.7, inconsistent: false },
        { name: "Salsa boloñesa", grams: 120, calories: 156, protein: 9.6, carbs: 6, fat: 10.2, inconsistent: false },
      ],
      confidence: "medium",
      notes: "Aceite estimado",
    });
  });

  it("tolerates code fences and text around the JSON", () => {
    const result = parseMealEstimate(`Aquí tienes:\n\`\`\`json\n${CLEAN}\n\`\`\`\nEspero que ayude.`);
    expect(result.ok && result.estimate.items).toHaveLength(2);
  });

  it("accepts a bare list, other key names and numbers written as text", () => {
    const result = parseMealEstimate(
      `[{"food":"Plátano","weight_g":"120 g","kcal":"107 kcal","proteins":1.3,"carbohydrates":27.4,"fats":0.4}]`,
    );
    expect(result).toEqual({
      ok: true,
      estimate: {
        title: "Plátano",
        items: [{ name: "Plátano", grams: 120, calories: 107, protein: 1.3, carbs: 27.4, fat: 0.4, inconsistent: false }],
        confidence: null,
        notes: null,
      },
    });
  });

  it("clamps negatives, drops nameless items and flags kcal that do not match the macros", () => {
    const result = parseMealEstimate(
      `{"items":[{"name":"Aceite","calories":-5,"fat":10},{"name":"Pollo","grams":0,"calories":500,"protein":20,"carbs":0,"fat":2},{"calories":100}]}`,
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.estimate.items).toEqual([
      { name: "Aceite", grams: null, calories: 0, protein: 0, carbs: 0, fat: 10, inconsistent: true },
      { name: "Pollo", grams: null, calories: 500, protein: 20, carbs: 0, fat: 2, inconsistent: true },
    ]);
    expect(result.estimate.title).toBe("Aceite, Pollo");
  });

  it("reports answers without JSON or without items", () => {
    expect(parseMealEstimate("No puedo ver bien la imagen.")).toEqual({ ok: false, error: "no_json" });
    expect(parseMealEstimate("broken {json")).toEqual({ ok: false, error: "no_json" });
    expect(parseMealEstimate(`{"items":[]}`)).toEqual({ ok: false, error: "no_items" });
    expect(parseMealEstimate(`{"title":"x"}`)).toEqual({ ok: false, error: "no_items" });
  });
});

describe("extractJson", () => {
  it("takes whichever JSON value opens first", () => {
    expect(extractJson(`Result: [{"name":"a"}] {`)).toEqual([{ name: "a" }]);
    expect(extractJson("nothing here")).toBeNull();
  });
});

describe("estimateToIngredients", () => {
  it("turns items into ingredient rows with grams as quantity", () => {
    const result = parseMealEstimate(`{"items":[{"name":"Arroz","grams":150,"calories":195,"protein":4,"carbs":42,"fat":0.4},{"name":"Salsa","calories":40}]}`);
    if (!result.ok) throw new Error("expected ok");
    expect(estimateToIngredients(result.estimate)).toEqual([
      { name: "Arroz", quantity: "150 g", calories: 195, protein: 4, carbs: 42, fat: 0.4 },
      { name: "Salsa", quantity: undefined, calories: 40, protein: 0, carbs: 0, fat: 0 },
    ]);
  });
});

describe("meal estimate prompts", () => {
  it("asks for the JSON shape in the app language", () => {
    const system = buildMealEstimateSystemPrompt("Spanish");
    expect(system).toContain(MEAL_ESTIMATE_SHAPE);
    expect(system).toContain("Write the title, the item names and the notes in Spanish.");
    expect(system.split("\n")).toHaveLength(9);
  });

  it("describes the photos and adds what the user wrote", () => {
    expect(buildMealEstimateUserText("", 1)).toBe("A photo of my meal.");
    expect(buildMealEstimateUserText("  con una cucharada de aceite ", 3)).toBe(
      "3 photos of the same meal from different angles (one may be a nutrition label).\nWhat I can add: con una cucharada de aceite",
    );
    expect(buildMealEstimateUserText("3 plátanos", 0)).toBe("Estimate this meal: 3 plátanos");
  });
});
