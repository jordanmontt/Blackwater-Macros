import { loadEnvFiles } from "./lib/env";

// Carga .env.local ANTES de importar los módulos que leen DATABASE_URL.
loadEnvFiles();

/**
 * Crea (o reinicia) el usuario "demo" con ~45 días de datos realistas para
 * poder explorar la aplicación: comidas en ambos modos, plantillas y pesos.
 *
 * Uso: npm run seed -- [usuario] [contraseña]
 * Por defecto: demo / demo1234
 */

const USERNAME = process.argv[2] ?? "demo";
const PASSWORD = process.argv[3] ?? "demo1234";

/** PRNG determinista: el mismo volcado siempre genera los mismos datos. */
function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function dateKeyOffset(daysAgo: number): string {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

async function main() {
  const [{ hashPassword }, { repositories }, { createMeal }, { createWeight }] =
    await Promise.all([
      import("../src/server/auth/password"),
      import("../src/server/composition"),
      import("../src/server/services/meals-service"),
      import("../src/server/services/weights-service"),
    ]);

  // Reinicio idempotente: borrar el usuario borra en cascada sus datos.
  const existing = await repositories.users.findByUsername(USERNAME);
  if (existing) {
    const { db } = await import("../src/server/db/client");
    const { users } = await import("../src/server/db/schema");
    const { eq } = await import("drizzle-orm");
    await db.delete(users).where(eq(users.id, existing.id));
    console.log(`· Usuario "${USERNAME}" existente eliminado junto a sus datos.`);
  }

  const user = await repositories.users.create({
    username: USERNAME,
    passwordHash: await hashPassword(PASSWORD),
  });
  console.log(`✔ Usuario demo creado (${user.id}). Contraseña: ${PASSWORD}`);

  // Calorie profile for demo user: male, 1990, 178cm, 3x/week 60min, 30min walking, deficit
  await repositories.settings.updateCalorieProfile(user.id, {
    gender: "male",
    birthYear: 1990,
    heightCm: 178,
    gymDaysPerWeek: 3,
    gymSessionMinutes: 60,
    walkingMinutesPerDay: 30,
    calorieGoal: "deficit",
  });

  const rand = mulberry32(20260823);

  interface IngredientSeed {
    name: string;
    quantity?: string;
    calories?: number;
    protein?: number;
    carbs?: number;
    fat?: number;
  }
  interface MealSeed {
    title: string;
    mode: "per_ingredient" | "total_only";
    ingredients: IngredientSeed[];
    totalCalories?: number;
    totalProtein?: number;
    totalCarbs?: number;
    totalFat?: number;
    probability: number;
  }

  const jitter = (value: number, spread: number) => Math.round(value + (rand() - 0.5) * spread);

  const breakfasts: MealSeed[] = [
    {
      title: "Desayuno",
      mode: "per_ingredient",
      probability: 0.95,
      ingredients: [
        { name: "4 huevos", quantity: "240 g", calories: 280, protein: 24, carbs: 2, fat: 20 },
        { name: "2 cucharillas de aceite de oliva", calories: 80, protein: 0, carbs: 0, fat: 9 },
        { name: "30-40 gramos queso Gouda", quantity: "35 g", calories: 115, protein: 8.4, carbs: 1, fat: 9 },
      ],
    },
    {
      title: "Desayuno",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Tostada integral", quantity: "60 g", calories: 150, protein: 6, carbs: 26, fat: 2 },
        { name: "Aguacate", quantity: "½ unidad", calories: 120, protein: 1.5, carbs: 6, fat: 11 },
        { name: "Café con leche", calories: 40, protein: 2, carbs: 4, fat: 2 },
      ],
    },
    {
      title: "Desayuno dulce",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Yogur griego", quantity: "170 g", calories: 100, protein: 17, carbs: 6, fat: 5 },
        { name: "Copos de avena", quantity: "40 g", calories: 150, protein: 5, carbs: 25, fat: 3 },
        { name: "Plátano", calories: 90, protein: 1, carbs: 22, fat: 0 },
      ],
    },
  ];

  const lunches: MealSeed[] = [
    {
      title: "Comida",
      mode: "total_only",
      probability: 1,
      ingredients: [{ name: "Menú del día (sin detallar)" }],
      totalCalories: 850,
      totalProtein: 45,
      totalCarbs: 80,
      totalFat: 35,
    },
    {
      title: "Comida",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Pechuga de pollo a la plancha", quantity: "180 g", calories: 300, protein: 55, carbs: 0, fat: 7 },
        { name: "Arroz blanco cocido", quantity: "150 g", calories: 195, protein: 4, carbs: 42, fat: 0 },
        { name: "Ensalada verde", calories: 50, protein: 1, carbs: 6, fat: 3 },
        { name: "Aceite de oliva", quantity: "1 cucharada", calories: 90, protein: 0, carbs: 0, fat: 10 },
      ],
    },
    {
      title: "Comida",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Lentejas guisadas", quantity: "350 g", calories: 420, protein: 27, carbs: 55, fat: 8 },
        { name: "Pan", quantity: "60 g", calories: 160, protein: 5, carbs: 30, fat: 2 },
      ],
    },
  ];

  const dinners: MealSeed[] = [
    {
      title: "Cena",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Salmón al horno", quantity: "150 g", calories: 310, protein: 34, carbs: 0, fat: 20 },
        { name: "Verduras asadas", calories: 90, protein: 3, carbs: 10, fat: 5 },
      ],
    },
    {
      title: "Cena",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Tortilla francesa", quantity: "3 huevos", calories: 210, protein: 18, carbs: 2, fat: 15 },
        { name: "Ensalada de tomate", calories: 60, protein: 1, carbs: 8, fat: 3 },
        { name: "Queso fresco", quantity: "100 g", calories: 90, protein: 12, carbs: 3, fat: 4 },
      ],
    },
    {
      title: "Cena ligera",
      mode: "total_only",
      probability: 1,
      ingredients: [],
      totalCalories: 380,
      totalProtein: 30,
      totalCarbs: 30,
      totalFat: 15,
    },
  ];

  const snacks: MealSeed[] = [
    {
      title: "Merienda",
      mode: "total_only",
      probability: 1,
      ingredients: [],
      totalCalories: 220,
      totalProtein: 14,
      totalCarbs: 25,
      totalFat: 8,
    },
    {
      title: "Merienda",
      mode: "per_ingredient",
      probability: 1,
      ingredients: [
        { name: "Puñado de almendras", quantity: "25 g", calories: 145, protein: 5, carbs: 5, fat: 12 },
        { name: "Manzana", calories: 80, protein: 0, carbs: 18, fat: 0 },
      ],
    },
  ];

  function pick(seeds: MealSeed[]): MealSeed {
    return seeds[Math.floor(rand() * seeds.length)];
  }

  const DAYS = 45;
  let mealCount = 0;

  for (let daysAgo = DAYS; daysAgo >= 0; daysAgo--) {
    const logDate = dateKeyOffset(daysAgo);
    const plan: MealSeed[] = [];

    if (rand() < 0.97) plan.push(pick(breakfasts));
    if (rand() < 0.98) plan.push(pick(lunches));
    if (rand() < 0.92) plan.push(pick(dinners));
    if (rand() < 0.55) plan.push(pick(snacks));

    for (const seed of plan) {
      if (seed.probability < 1 && rand() > seed.probability) continue;
      const ingredients = seed.ingredients.map((ingredient) => ({
        ...ingredient,
        ...(ingredient.calories !== undefined
          ? { calories: jitter(ingredient.calories, ingredient.calories * 0.08) }
          : {}),
        ...(ingredient.protein !== undefined && ingredient.protein > 0
          ? { protein: jitter(ingredient.protein, ingredient.protein * 0.06) }
          : {}),
        ...(ingredient.carbs !== undefined && ingredient.carbs > 0
          ? { carbs: jitter(ingredient.carbs, ingredient.carbs * 0.06) }
          : {}),
        ...(ingredient.fat !== undefined && ingredient.fat > 0
          ? { fat: jitter(ingredient.fat, ingredient.fat * 0.06) }
          : {}),
      }));
      await createMeal(repositories.meals, user.id, {
        logDate,
        title: seed.title,
        notes: null,
        entryMode: seed.mode,
        ingredients,
        totalCalories:
          seed.mode === "total_only" ? jitter(seed.totalCalories ?? 500, 120) : null,
        totalProtein:
          seed.mode === "total_only" ? jitter(seed.totalProtein ?? 30, 12) : null,
        totalCarbs:
          seed.mode === "total_only" ? jitter(seed.totalCarbs ?? 40, 10) : null,
        totalFat:
          seed.mode === "total_only" ? jitter(seed.totalFat ?? 20, 8) : null,
      });
      mealCount++;
    }

    // Pesos: la mayoría de días uno, algunos dos registros, tendencia suave a la baja.
    if (rand() < 0.9) {
      const baseWeight = 84 - ((DAYS - daysAgo) / DAYS) * 1.8; // ~-1.8 kg en 45 días
      const weightKg = Math.round((baseWeight + (rand() - 0.5) * 0.7) * 10) / 10;
      const baseBfPct = 19 - ((DAYS - daysAgo) / DAYS) * 3; // ~19% → ~16%
      const bodyFatPct = rand() < 0.75
        ? Math.round((baseBfPct + (rand() - 0.5) * 1.6) * 10) / 10
        : null;
      const measuredAt = new Date(`${logDate}T07:45:00`);
      await createWeight(repositories.weights, user.id, {
        measuredAt: measuredAt.toISOString(),
        weightKg,
        bodyFatPct,
        note: null,
      });
      if (rand() < 0.12) {
        const eveningWeight = Math.round((weightKg + 0.4 + rand() * 0.3) * 10) / 10;
        await createWeight(repositories.weights, user.id, {
          measuredAt: new Date(`${logDate}T21:30:00`).toISOString(),
          weightKg: eveningWeight,
          bodyFatPct: null,
          note: "tras entrenar",
        });
      }
    }
  }

  // Plantillas listas para aplicar desde "Hoy".
  const { createTemplate } = await import("../src/server/services/templates-service");
  await createTemplate(
    repositories.templates,
    user.id,
    {
      name: "Desayuno",
      title: "Desayuno",
      notes: null,
      ingredients: breakfasts[0].ingredients,
    },
  );
  await createTemplate(repositories.templates, user.id, {
    name: "Cena ligera",
    title: "Cena ligera",
    notes: null,
    ingredients: [],
  });

  console.log(`✔ Sembrados ${mealCount} comidas, pesos de ${DAYS} días y 2 plantillas.`);
  console.log("  Entra con estas credenciales en /login.");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("✘ Error:", error.message);
    process.exit(1);
  });
