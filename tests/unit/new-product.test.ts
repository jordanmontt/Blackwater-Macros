import { describe, expect, it } from "vitest";
import {
  buildLabelReadingSystemPrompt,
  newProductPer100g,
  newProductProblems,
  newProductToFoodProduct,
  offFieldsOutcome,
  offImageFields,
  offImageOutcome,
  offKnownProduct,
  offProductFields,
  parseLabelReading,
  searchOwnProducts,
  servingAmount,
  type NewProduct,
  type OffWriteContext,
} from "../../src/lib/core/new-product";
import type { FoodProduct } from "../../src/lib/core/foods";

const MILK: NewProduct = {
  code: "7771234000123",
  name: " Leche entera ",
  brand: "PIL",
  basis: "serving",
  servingSize: "200 ml",
  calories: 124,
  protein: 6.4,
  carbs: 9.6,
  fat: 6.6,
};

const OATS: NewProduct = {
  code: "8410128005432",
  name: "Oat drink",
  brand: null,
  basis: "100ml",
  servingSize: null,
  calories: 46,
  protein: 1,
  carbs: null,
  fat: 1.5,
};

const CONTEXT: OffWriteContext = {
  lang: "es",
  userId: "blackwater-macros-app",
  password: "secret",
  appVersion: "1.4.0",
  appUuid: "0b4e1c52-7d1a-4f0e-9a7b-3c2d1e0f9a8b",
  existing: false,
};

describe("newProductPer100g / servingAmount", () => {
  it("turns a label per serving (200 ml) into per 100 ml", () => {
    expect(servingAmount(MILK)).toBe(200);
    expect(newProductPer100g(MILK)).toEqual({ calories: 62, protein: 3.2, carbs: 4.8, fat: 3.3 });
  });

  it("keeps a label per 100 g or ml as it is; missing carbs or fat count as 0", () => {
    expect(newProductPer100g(OATS)).toEqual({ calories: 46, protein: 1, carbs: 0, fat: 1.5 });
  });

  it("rounds to one decimal", () => {
    expect(newProductPer100g({ ...MILK, servingSize: "30 g", calories: 113, protein: 2.5 })).toEqual({
      calories: 376.7,
      protein: 8.3,
      carbs: 32,
      fat: 22,
    });
  });
});

describe("newProductProblems", () => {
  it("accepts a complete label", () => {
    expect(newProductProblems(MILK)).toEqual([]);
    expect(newProductProblems(OATS)).toEqual([]);
  });

  it("needs a name, the kcal and the protein, in screen order", () => {
    expect(newProductProblems({ ...OATS, name: "  ", calories: null, protein: null })).toEqual([
      "name",
      "calories",
      "protein",
    ]);
  });

  it("needs a serving size it can read when the values are per serving", () => {
    expect(newProductProblems({ ...MILK, servingSize: null })).toEqual(["serving"]);
    expect(newProductProblems({ ...MILK, servingSize: "1 vaso" })).toEqual(["serving"]);
  });

  it("refuses values impossible for 100 g (a misplaced digit or the wrong basis)", () => {
    expect(newProductProblems({ ...OATS, calories: 4600 })).toEqual(["tooHigh"]);
    expect(newProductProblems({ ...OATS, basis: "100g", protein: 60, carbs: 50 })).toEqual(["tooHigh"]);
    expect(newProductProblems({ ...MILK, servingSize: "20 ml" })).toEqual(["tooHigh"]);
  });
});

describe("newProductToFoodProduct", () => {
  it("is the product the portion screen and search use", () => {
    expect(newProductToFoodProduct(MILK)).toEqual({
      code: "7771234000123",
      name: "Leche entera",
      brand: "PIL",
      per100g: { calories: 62, protein: 3.2, carbs: 4.8, fat: 3.3 },
      servingGrams: 200,
      incomplete: false,
    });
    expect(newProductToFoodProduct({ ...OATS, brand: "  " })).toMatchObject({ brand: null, servingGrams: null, incomplete: true });
  });
});

describe("offProductFields / offImageFields", () => {
  it("sends the label as printed, with the account and the app's anonymous install id", () => {
    expect(offProductFields(MILK, CONTEXT)).toEqual([
      ["code", "7771234000123"],
      ["user_id", "blackwater-macros-app"],
      ["password", "secret"],
      ["app_name", "Blackwater Macros"],
      ["app_version", "1.4.0"],
      ["app_uuid", "0b4e1c52-7d1a-4f0e-9a7b-3c2d1e0f9a8b"],
      ["lc", "es"],
      ["product_name_es", "Leche entera"],
      ["add_brands", "PIL"],
      ["nutrition_data_per", "serving"],
      ["serving_size", "200 ml"],
      ["nutriment_energy-kcal", "124"],
      ["nutriment_energy-kcal_unit", "kcal"],
      ["nutriment_proteins", "6.4"],
      ["nutriment_proteins_unit", "g"],
      ["nutriment_carbohydrates", "9.6"],
      ["nutriment_carbohydrates_unit", "g"],
      ["nutriment_fat", "6.6"],
      ["nutriment_fat_unit", "g"],
      ["comment", "Blackwater Macros 1.4.0"],
    ]);
  });

  it("leaves out what the user left empty", () => {
    const fields = offProductFields(OATS, { ...CONTEXT, lang: "en" });
    expect(fields.map(([key]) => key)).not.toContain("add_brands");
    expect(fields.map(([key]) => key)).not.toContain("nutriment_carbohydrates");
    expect(fields).toContainEqual(["product_name_en", "Oat drink"]);
    expect(fields).toContainEqual(["nutrition_data_per", "100ml"]);
  });

  it("only adds the nutrition to a product Open Food Facts already had", () => {
    const keys = offProductFields(MILK, { ...CONTEXT, existing: true }).map(([key]) => key);
    expect(keys).not.toContain("product_name_es");
    expect(keys).not.toContain("add_brands");
    expect(keys).toContain("nutriment_energy-kcal");
  });

  it("sends the photo as the nutrition label in the app language", () => {
    expect(offImageFields("7771234000123", { ...CONTEXT, lang: "fr" })).toEqual([
      ["code", "7771234000123"],
      ["user_id", "blackwater-macros-app"],
      ["password", "secret"],
      ["app_name", "Blackwater Macros"],
      ["app_version", "1.4.0"],
      ["app_uuid", "0b4e1c52-7d1a-4f0e-9a7b-3c2d1e0f9a8b"],
      ["imagefield", "nutrition_fr"],
    ]);
  });
});

describe("offFieldsOutcome / offImageOutcome", () => {
  it("reads Open Food Facts' answers", () => {
    expect(offFieldsOutcome(200, { status: 1, status_verbose: "fields saved" })).toBe("ok");
    expect(offFieldsOutcome(200, { status: 0, status_verbose: "no user credentials" })).toBe("account");
    expect(offFieldsOutcome(200, { status: 0, status_verbose: "no code or invalid code" })).toBe("rejected");
    expect(offFieldsOutcome(200, null)).toBe("retry");
    expect(offImageOutcome(200, { status: "status ok", imgid: 1 })).toBe("ok");
    expect(offImageOutcome(200, { status: "status not ok", error: "This picture has already been sent." })).toBe("ok");
    expect(offImageOutcome(200, { status: "status not ok", error: "image too small" })).toBe("rejected");
  });

  it("retries what may work later and stops on the rest", () => {
    for (const status of [408, 429, 500, 502, 503]) expect(offFieldsOutcome(status, null)).toBe("retry");
    expect(offFieldsOutcome(403, null)).toBe("account");
    expect(offImageOutcome(401, null)).toBe("account");
    expect(offFieldsOutcome(400, null)).toBe("rejected");
  });
});

describe("offKnownProduct", () => {
  it("starts the form from what Open Food Facts knows", () => {
    const json = { status: 1, product: { product_name: "Galletas María", product_name_es: "Galletas María Oro", brands: "Fontaneda, Mondelez" } };
    expect(offKnownProduct(json, "es")).toEqual({ name: "Galletas María Oro", brand: "Fontaneda" });
    expect(offKnownProduct(json, "fr")).toEqual({ name: "Galletas María", brand: "Fontaneda" });
    expect(offKnownProduct({ status: 0 }, "es")).toEqual({ name: null, brand: null });
  });
});

describe("searchOwnProducts", () => {
  const products: FoodProduct[] = [
    newProductToFoodProduct(MILK),
    newProductToFoodProduct(OATS),
    { ...newProductToFoodProduct(OATS), code: "1", name: "Yogur de avena", brand: "Hacendado" },
  ];

  it("finds by the start of any word of the name or brand, without accents", () => {
    expect(searchOwnProducts(products, "lech").map((p) => p.name)).toEqual(["Leche entera"]);
    expect(searchOwnProducts(products, "pil").map((p) => p.name)).toEqual(["Leche entera"]);
    expect(searchOwnProducts(products, "Avéna yog").map((p) => p.name)).toEqual(["Yogur de avena"]);
    expect(searchOwnProducts(products, "tera")).toEqual([]);
    expect(searchOwnProducts(products, "  ")).toEqual([]);
  });
});

describe("parseLabelReading", () => {
  it("reads the model's JSON, also inside a fence", () => {
    const answer =
      '```json\n{"name":"Leche entera","brand":"PIL","basis":"serving","servingSize":"200 ml","calories":124,"protein":"6,4 g","carbs":9.6,"fat":null}\n```';
    expect(parseLabelReading(answer)).toEqual({
      name: "Leche entera",
      brand: "PIL",
      basis: "serving",
      servingSize: "200 ml",
      calories: 124,
      protein: 6.4,
      carbs: 9.6,
      fat: null,
    });
  });

  it("understands the basis written in other ways", () => {
    const read = (basis: unknown, servingSize: string | null = null) =>
      parseLabelReading(JSON.stringify({ basis, servingSize, calories: 50, protein: 1 }))?.basis;
    expect(read("per 100 ml")).toBe("100ml");
    expect(read("100 g")).toBe("100g");
    expect(read("por porción")).toBe("serving");
    expect(read(null, "30 g")).toBe("serving");
    expect(read(null)).toBe("100g");
  });

  it("gives nothing when it read neither kcal nor protein", () => {
    expect(parseLabelReading('{"name":"Leche","calories":null,"protein":null}')).toBeNull();
    expect(parseLabelReading("I can't read this label.")).toBeNull();
  });
});

describe("buildLabelReadingSystemPrompt", () => {
  it("asks for the label as printed, never estimated", () => {
    const prompt = buildLabelReadingSystemPrompt("Spanish");
    expect(prompt).toContain("Never estimate or invent a value");
    expect(prompt).toContain('"basis": "100g" | "100ml" | "serving"');
    expect(prompt).toContain("write it in Spanish");
  });
});
