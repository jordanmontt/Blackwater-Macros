import { describe, expect, it } from "vitest";
import {
  buildLabelReadingSystemPrompt,
  correctionBaseline,
  correctionChanges,
  newProductInBasis,
  newProductPer100g,
  newProductProblems,
  newProductToFoodProduct,
  offFieldsOutcome,
  offImageFields,
  offImageOutcome,
  offKnownProduct,
  offProductFields,
  parseLabelReading,
  packageLang,
  parseOffCorrection,
  searchOwnProducts,
  servingAmount,
  type NewProduct,
  type OffCorrection,
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
  keepPer: null,
};

/** Open Food Facts has the milk per 100 ml, with the old recipe's values. */
const MILK_ON_OFF: OffCorrection = {
  per: "100g",
  lang: "es",
  base: { ...MILK, name: "Leche entera", basis: "100g", servingSize: "200 ml", calories: 64, protein: 3.2, carbs: 4.8, fat: null },
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
      ["lang", "es"],
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
    // Its main language stays Open Food Facts'.
    expect(keys).not.toContain("lang");
    expect(keys).not.toContain("add_brands");
    expect(keys).toContain("nutriment_energy-kcal");
  });

  it("names a new product in the package's language, whatever the app's", () => {
    const fields = offProductFields(MILK, { ...CONTEXT, lang: "pt" });
    expect(fields).toContainEqual(["lc", "pt"]);
    expect(fields).toContainEqual(["lang", "pt"]);
    expect(fields).toContainEqual(["product_name_pt", "Leche entera"]);
  });

  it("sends the photo as the nutrition label in the package's language", () => {
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
    const json = {
      status: 1,
      product: { product_name: "Galletas María", product_name_es: "Galletas María Oro", brands: "Fontaneda, Mondelez", lang: "es" },
    };
    expect(offKnownProduct(json, "es")).toEqual({ name: "Galletas María Oro", brand: "Fontaneda", lang: "es" });
    expect(offKnownProduct(json, "fr")).toEqual({ name: "Galletas María", brand: "Fontaneda", lang: "es" });
    expect(offKnownProduct({ status: 0 }, "es")).toEqual({ name: null, brand: null, lang: null });
  });

  it("reads the main language only as a two-letter code", () => {
    expect(packageLang(" ES ")).toBe("es");
    expect(packageLang("spanish")).toBeNull();
    expect(packageLang(null)).toBeNull();
    expect(offKnownProduct({ product: { lc: "pt" } }, "es").lang).toBe("pt");
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
      '```json\n{"name":"Leche entera","brand":"PIL","language":"es","basis":"serving","servingSize":"200 ml","calories":124,"protein":"6,4 g","carbs":9.6,"fat":null}\n```';
    expect(parseLabelReading(answer)).toEqual({
      name: "Leche entera",
      language: "es",
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
    expect(prompt).toContain("ISO 639-1 code of the language the label is written in");
  });
});

describe("newProductInBasis", () => {
  it("keeps a label already in the basis Open Food Facts stores", () => {
    expect(newProductInBasis(MILK, "serving")).toBe(MILK);
    expect(newProductInBasis(OATS, "100g")).toBe(OATS);
  });

  it("converts a label per serving to per 100, and per 100 to per serving", () => {
    expect(newProductInBasis(MILK, "100g")).toEqual({ ...MILK, basis: "100g", calories: 62, protein: 3.2, carbs: 4.8, fat: 3.3 });
    expect(newProductInBasis({ ...OATS, servingSize: "250 ml" }, "serving")).toEqual({
      ...OATS,
      servingSize: "250 ml",
      basis: "serving",
      calories: 115,
      protein: 2.5,
      carbs: null,
      fat: 3.75,
    });
  });

  it("can't convert without a serving size", () => {
    expect(newProductInBasis(OATS, "serving")).toBeNull();
    expect(newProductProblems(OATS, "serving")).toEqual(["serving"]);
    expect(newProductProblems({ ...OATS, servingSize: "250 ml" }, "serving")).toEqual([]);
  });
});

describe("correctionBaseline / correctionChanges", () => {
  it("shows what Open Food Facts has in the basis and serving of the form", () => {
    expect(correctionBaseline(MILK_ON_OFF, MILK_ON_OFF.base)).toEqual({ calories: 64, protein: 3.2, carbs: 4.8, fat: null });
    expect(correctionBaseline(MILK_ON_OFF, MILK)).toEqual({ calories: 128, protein: 6.4, carbs: 9.6, fat: null });
    expect(correctionBaseline(MILK_ON_OFF, { ...MILK, servingSize: null })).toBeNull();
  });

  it("lists only the values that really change; an empty field changes nothing", () => {
    expect(correctionChanges(MILK_ON_OFF, MILK_ON_OFF.base)).toEqual([]);
    expect(correctionChanges(MILK_ON_OFF, MILK)).toEqual(["calories", "fat"]);
    expect(correctionChanges(MILK_ON_OFF, { ...MILK_ON_OFF.base, calories: 64.3, protein: null })).toEqual([]);
  });
});

describe("parseOffCorrection", () => {
  const json = {
    status: 1,
    product: {
      product_name_es: "Leche entera",
      brands: "PIL",
      serving_size: "200 ml",
      nutrition_data_per: "100g",
      lang: "es",
      nutriments: { "energy-kcal_100g": 64, proteins_100g: 3.2, carbohydrates_100g: 4.8, "energy-kcal_serving": 128 },
    },
  };

  it("starts from Open Food Facts' values in the basis it stores, missing ones empty", () => {
    expect(parseOffCorrection(json, "7771234000123", "es")).toEqual(MILK_ON_OFF);
  });

  it("reads a product stored per serving, and kJ when kcal is missing", () => {
    const perServing = {
      product: { ...json.product, nutrition_data_per: "serving", nutriments: { "energy-kj_serving": 535.6, proteins_serving: 6.4 } },
    };
    expect(parseOffCorrection(perServing, "7771234000123", "es")).toEqual({
      per: "serving",
      lang: "es",
      base: { ...MILK_ON_OFF.base, basis: "serving", calories: 128, protein: 6.4, carbs: null, fat: null },
    });
    expect(parseOffCorrection({ status: 0 }, "1", "es")).toBeNull();
  });
});

describe("offProductFields for a correction", () => {
  it("sends the values in the basis Open Food Facts stores, never the name or brand", () => {
    const fields = offProductFields(MILK, { ...CONTEXT, existing: true, keepPer: "100g" });
    expect(fields).toContainEqual(["nutrition_data_per", "100g"]);
    expect(fields).toContainEqual(["nutriment_energy-kcal", "62"]);
    expect(fields).toContainEqual(["nutriment_fat", "3.3"]);
    expect(fields).toContainEqual(["serving_size", "200 ml"]);
    expect(fields.map(([key]) => key)).not.toContain("product_name_es");
  });
});
