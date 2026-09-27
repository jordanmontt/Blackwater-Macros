import { describe, expect, it } from "vitest";
import {
  foodToIngredient,
  normalizeText,
  parseOffProduct,
  parseGenericIndex,
  parseOffSearch,
  parseServingGrams,
  queryAlternatives,
  scalePer100g,
  searchGenericFoods,
  type GenericFood,
} from "../../src/lib/core/foods";

const OFF_PRODUCT = `{"code":"3033490004743","status":1,"product":{"code":"3033490004743","product_name":"Yaourt à la grecque","brands":"Danone, Oikos","serving_size":"125 g","serving_quantity":"125","nutriments":{"energy-kcal_100g":97,"proteins_100g":"3.2","carbohydrates_100g":4.5,"fat_100g":7.4}}}`;
const OFF_KJ_ONLY = `{"status":1,"product":{"code":"8480000123456","product_name":"Galletas","serving_size":"2 galletas (25g)","nutriments":{"energy_100g":1000,"carbohydrates_100g":70,"fat_100g":8}}}`;
const OFF_SEARCH = `{"hits":[{"code":"8480000592170","product_name":"Yogur griego natural","brands":["Hacendado"],"nutriments":{"energy-kcal_100g":122,"proteins_100g":3.5,"carbohydrates_100g":4.2,"fat_100g":10}},{"code":"1","product_name":"Sin energía","nutriments":{}}]}`;

const FOODS: GenericFood[] = [
  { id: "usda:1", source: "usda", names: { en: "Yogurt, Greek, plain, nonfat", es: "Yogur griego natural desnatado" }, per100g: { calories: 59, protein: 10.2, carbs: 3.6, fat: 0.4 } },
  { id: "ch:2", source: "ch", names: { de: "Griechischer Joghurt", fr: "Yogourt grec", it: "Yogurt greco", en: "Greek yogurt" }, per100g: { calories: 115, protein: 4, carbs: 4, fat: 9 } },
  { id: "usda:3", source: "usda", names: { en: "Bananas, raw", es: "Plátano crudo" }, per100g: { calories: 89, protein: 1.1, carbs: 22.8, fat: 0.3 } },
  { id: "ciqual:4", source: "ciqual", names: { fr: "Banane, pulpe, crue", en: "Banana, pulp, raw" }, per100g: { calories: 90, protein: 1.1, carbs: 20.5, fat: 0.3 } },
];

describe("scalePer100g / foodToIngredient", () => {
  it("scales per-100 g values to a portion: kcal integer, macros 1 decimal", () => {
    expect(scalePer100g({ calories: 59, protein: 10, carbs: 3.6, fat: 0.4 }, 150)).toEqual({
      calories: 89,
      protein: 15,
      carbs: 5.4,
      fat: 0.6,
    });
  });

  it("builds an ingredient row with the quantity in grams", () => {
    expect(foodToIngredient("Plátano", { calories: 89, protein: 1.1, carbs: 22.8, fat: 0.3 }, 120)).toEqual({
      name: "Plátano",
      quantity: "120 g",
      calories: 107,
      protein: 1.3,
      carbs: 27.4,
      fat: 0.4,
    });
    expect(foodToIngredient("Aceite", { calories: 884, protein: 0, carbs: 0, fat: 100 }, 12.5).quantity).toBe("12.5 g");
  });
});

describe("parseServingGrams", () => {
  it("reads grams or millilitres from a serving description", () => {
    expect(parseServingGrams("30 g")).toBe(30);
    expect(parseServingGrams("1 cup (240 ml)")).toBe(240);
    expect(parseServingGrams("2 galletas (25g)")).toBe(25);
    expect(parseServingGrams("1,5 gramos")).toBe(1.5);
  });

  it("returns null when there is no amount", () => {
    expect(parseServingGrams("1 unidad")).toBeNull();
    expect(parseServingGrams("0 g")).toBeNull();
    expect(parseServingGrams(null)).toBeNull();
  });
});

describe("normalizeText", () => {
  it("ignores case, accents and extra spaces", () => {
    expect(normalizeText("  Yogúr  Griego ")).toBe("yogur griego");
    expect(normalizeText("Crème Brûlée")).toBe("creme brulee");
    expect(normalizeText("Piña")).toBe("pina");
  });
});

describe("Open Food Facts parsing", () => {
  it("reads a product: name, first brand, serving and per-100 g macros", () => {
    expect(parseOffProduct(JSON.parse(OFF_PRODUCT))).toEqual({
      code: "3033490004743",
      name: "Yaourt à la grecque",
      brand: "Danone",
      per100g: { calories: 97, protein: 3.2, carbs: 4.5, fat: 7.4 },
      servingGrams: 125,
      incomplete: false,
    });
  });

  it("falls back to kJ ÷ 4.184 and flags missing macros", () => {
    const product = parseOffProduct(JSON.parse(OFF_KJ_ONLY))!;
    expect(product.per100g).toEqual({ calories: 239, protein: 0, carbs: 70, fat: 8 });
    expect(product.incomplete).toBe(true);
    expect(product.servingGrams).toBe(25);
    expect(product.brand).toBeNull();
  });

  it("returns null for unknown products or products without energy", () => {
    expect(parseOffProduct(JSON.parse(`{"status":0,"status_verbose":"product not found"}`))).toBeNull();
    expect(parseOffProduct(JSON.parse(`{"status":1,"product":{"product_name":"X","nutriments":{}}}`))).toBeNull();
    expect(parseOffProduct(JSON.parse(`"nonsense"`))).toBeNull();
  });

  it("reads search results and skips products without energy", () => {
    const results = parseOffSearch(JSON.parse(OFF_SEARCH));
    expect(results).toHaveLength(1);
    expect(results[0].name).toBe("Yogur griego natural");
    expect(results[0].brand).toBe("Hacendado");
    expect(results[0].servingGrams).toBeNull();
    expect(parseOffSearch(JSON.parse(`{"products":[${JSON.stringify(JSON.parse(OFF_PRODUCT).product)}]}`))).toHaveLength(1);
    expect(parseOffSearch(JSON.parse(`{"error":"x"}`))).toEqual([]);
  });
});

describe("searchGenericFoods", () => {
  const ids = (query: string, lang: "es" | "en" = "es") => searchGenericFoods(FOODS, query, lang).map((m) => m.food.id);

  it("matches every word of the query at the start of a word, in any language", () => {
    expect(ids("yogur griego")).toEqual(["usda:1"]);
    // «platano» also finds «Banana» through the Spanish synonyms.
    expect(ids("platano")).toEqual(["usda:3", "ciqual:4"]);
    expect(ids("joghurt")).toEqual(["ch:2"]);
    expect(ids("gurt")).toEqual([]);
    expect(ids("   ")).toEqual([]);
  });

  it("ranks exact names first and shows the name in the app language", () => {
    const matches = searchGenericFoods(FOODS, "greek yogurt", "es");
    expect(matches.map((m) => m.food.id)).toEqual(["ch:2", "usda:1"]);
    expect(matches.map((m) => m.name)).toEqual(["Greek yogurt", "Yogur griego natural desnatado"]);
  });

  it("breaks ties by shorter display name and honours the limit", () => {
    expect(ids("ban")).toEqual(["usda:3", "ciqual:4"]);
    expect(searchGenericFoods(FOODS, "ban", "es", 1)).toHaveLength(1);
  });
});

describe("Spanish first: plurals and synonyms", () => {
  it("expands a query word with its singular and Spain/Latin-America synonyms", () => {
    expect(queryAlternatives("fresas")).toEqual(["fresas", "fresa", "frutilla"]);
    expect(queryAlternatives("limones")).toEqual(["limones", "limon", "limone"]);
    expect(queryAlternatives("papa")).toEqual(["papa", "patata"]);
    expect(queryAlternatives("pollo")).toEqual(["pollo"]);
  });

  it("finds «patata» when searching «papas cocidas» and «plátano» when searching «banana»", () => {
    const foods: GenericFood[] = [
      { id: "a", source: "ch", names: { es: "Patata cocida", en: "Potato, boiled" }, per100g: { calories: 77, protein: 2, carbs: 17, fat: 0.1 } },
      { id: "b", source: "ch", names: { es: "Papaya", en: "Papaya" }, per100g: { calories: 43, protein: 0.5, carbs: 11, fat: 0.3 } },
      { id: "c", source: "ch", names: { es: "Plátano", en: "Banana" }, per100g: { calories: 89, protein: 1.1, carbs: 23, fat: 0.3 } },
    ];
    const ids = (query: string) => searchGenericFoods(foods, query, "es").map((m) => m.food.id);
    expect(ids("papas cocidas")).toEqual(["a"]);
    expect(ids("jugo")).toEqual([]);
    expect(searchGenericFoods(foods, "banana", "es").map((m) => m.name)).toEqual(["Plátano"]);
  });
});

describe("parseGenericIndex", () => {
  it("reads the bundled index and skips broken entries", () => {
    const json = JSON.parse(
      `{"version":1,"foods":[{"id":"ch:1","s":"ch","n":{"es":"Manzana","en":"Apple, fresh","xx":"?"},"v":[52,0.3,11.4,0.2]},{"id":"ch:2","s":"ch","n":{},"v":[1,2,3,4]},{"id":"ch:3","n":{"es":"Sin valores"}}]}`,
    );
    expect(parseGenericIndex(json)).toEqual([
      { id: "ch:1", source: "ch", names: { es: "Manzana", en: "Apple, fresh" }, per100g: { calories: 52, protein: 0.3, carbs: 11.4, fat: 0.2 } },
    ]);
    expect(parseGenericIndex(JSON.parse(`{"nope":1}`))).toEqual([]);
  });
});

