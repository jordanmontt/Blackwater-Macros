/**
 * Writes the web demo dataset (`buildDemoStore`: 45 days of meals and weigh-ins
 * ending today) and the demo person's profile as the app's export file
 * (`<out-dir>/backup.json`), for the store screenshots (`scripts/screenshots.sh`,
 * docs/RELEASING.md).
 *
 *   npx tsx scripts/demo-backup.ts <out-dir> [es|en]
 *
 * With `en`, meal and food names are translated so English screenshots don't
 * show Spanish food.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { buildBackup } from "../src/lib/core/backup";
import { buildDemoStore } from "../src/lib/demo-store";

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
  console.error("Usage: npx tsx scripts/demo-backup.ts <out-dir> [es|en]");
  process.exit(1);
}
const name = (text: string | null | undefined) => (text && lang === "en" ? (ENGLISH[text] ?? text) : text ?? null);

const store = buildDemoStore();
const meals = store.meals.map((meal) => ({
  ...meal,
  title: name(meal.title) ?? meal.title,
  ingredients: meal.ingredients.map((ingredient) => ({
    ...ingredient,
    name: name(ingredient.name) ?? ingredient.name,
    quantity: name(ingredient.quantity) ?? undefined,
  })),
}));

const file = buildBackup(
  {
    // The demo person (the profile the screenshots show).
    profile: {
      gender: "male",
      birthYear: 1990,
      heightCm: 178,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: "cut",
    },
    profileHistory: [],
    weights: store.weights,
    meals,
    templates: [],
  },
  new Date().toISOString(),
);

mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, "backup.json"), file);
console.log(`${meals.length} meals and ${store.weights.length} weigh-ins (${lang}) in ${join(outDir, "backup.json")}`);
