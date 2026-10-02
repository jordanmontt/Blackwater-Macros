/**
 * Writes the web demo dataset (`buildDemoStore`: 45 days of meals and weigh-ins
 * ending today) as the app's CSV backup files, for the store screenshots
 * (`scripts/screenshots.sh`, docs/RELEASING.md).
 *
 *   npx tsx scripts/demo-csv.ts <out-dir> [es|en]
 *
 * With `en`, meal and food names are translated so English screenshots don't
 * show Spanish food.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { buildDemoStore } from "../src/lib/demo-store";
import { buildMealsCsv, buildWeightsCsv } from "../src/server/services/export-service";
import type { MealRow, WeightRow } from "../src/server/db/schema";

const ENGLISH: Record<string, string> = {
  Desayuno: "Breakfast",
  "Desayuno dulce": "Sweet breakfast",
  Comida: "Lunch",
  Cena: "Dinner",
  "Cena ligera": "Light dinner",
  Merienda: "Snack",
  "Batido post-entreno": "Post-workout shake",
  "4 huevos": "4 eggs",
  "2 cucharillas de aceite de oliva": "2 teaspoons of olive oil",
  "30-40 gramos queso Gouda": "30-40 g Gouda cheese",
  "Pan de centeno": "Rye bread",
  "2 tostadas integrales": "2 slices of wholemeal toast",
  Aguacate: "Avocado",
  "½ unidad": "½",
  "Jamón serrano": "Serrano ham",
  "Café con leche": "Coffee with milk",
  "Yogur griego": "Greek yogurt",
  "Copos de avena": "Rolled oats",
  Plátano: "Banana",
  "Crema de cacahuete": "Peanut butter",
  "Menú del día (sin detallar)": "Set menu (not itemised)",
  "Pechuga de pollo a la plancha": "Grilled chicken breast",
  "Arroz blanco cocido": "Cooked white rice",
  "Ensalada verde": "Green salad",
  "Aceite de oliva": "Olive oil",
  "1 cucharada": "1 tablespoon",
  "Lentejas guisadas": "Lentil stew",
  Pan: "Bread",
  Naranja: "Orange",
  "Salmón al horno": "Baked salmon",
  "Verduras asadas": "Roasted vegetables",
  "Patata cocida": "Boiled potato",
  "Tortilla francesa": "Omelette",
  "3 huevos": "3 eggs",
  "Ensalada de tomate": "Tomato salad",
  "Queso fresco": "Fresh cheese",
  "Puñado de almendras": "Handful of almonds",
  Manzana: "Apple",
  "Proteína whey": "Whey protein",
  "Leche semidesnatada": "Semi-skimmed milk",
};

const [outDir, lang = "es"] = process.argv.slice(2);
if (!outDir || (lang !== "es" && lang !== "en")) {
  console.error("Usage: npx tsx scripts/demo-csv.ts <out-dir> [es|en]");
  process.exit(1);
}
const name = (text: string | null | undefined) => (text && lang === "en" ? (ENGLISH[text] ?? text) : text ?? null);

const store = buildDemoStore();
const meals = store.meals.map((meal) => ({
  ...meal,
  title: name(meal.title),
  ingredients: meal.ingredients.map((ingredient) => ({
    ...ingredient,
    name: name(ingredient.name),
    quantity: name(ingredient.quantity) ?? undefined,
  })),
})) as unknown as MealRow[];
const weights = store.weights.map((weight) => ({ ...weight, measuredAt: new Date(weight.measuredAt) })) as unknown as WeightRow[];

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, "meals.csv"), buildMealsCsv(meals));
writeFileSync(join(outDir, "weights.csv"), buildWeightsCsv(weights));
console.log(`${meals.length} meals and ${weights.length} weigh-ins (${lang}) in ${outDir}`);
