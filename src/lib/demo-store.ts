import { todayKey, addDaysToKey } from "./dates";
import { resolveMealTotals } from "./nutrition";
import type {
  CalorieProfile,
  IngredientInput,
  MealDTO,
  MealTemplateDTO,
  WeightDTO,
} from "./types";
import type { MealPayload, WeightPayload, TemplatePayload } from "./api";

export const DEMO_COOKIE_NAME = "bw_demo";
const STORAGE_KEY = "blackwater_demo_store_v1";

/** The demo user's fixed calorie profile shown across the app. */
export const DEMO_CALORIE_PROFILE: CalorieProfile = {
  gender: "male",
  birthYear: 1990,
  heightCm: 178,
  gymDaysPerWeek: 3,
  gymSessionMinutes: 60,
  walkingMinutesPerDay: 30,
  calorieGoal: "cut",
};

export const DEMO_USERNAME = "Demo";

/** Internal meal record: a MealDTO plus the client-only sort fields. */
interface StoredMeal extends MealDTO {
  order: number;
  createdAt: number;
}

interface DemoStore {
  meals: StoredMeal[];
  templates: MealTemplateDTO[];
  weights: WeightDTO[];
}

function newId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `demo-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

// ---------------------------------------------------------------------------
// Demo-mode flag
// ---------------------------------------------------------------------------

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie
    .split("; ")
    .find((row) => row.trim().startsWith(`${name}=`));
  return match ? decodeURIComponent(match.trim().split("=")[1]) : null;
}

/** Whether the browser is currently in client-side demo mode. */
export function isDemoMode(): boolean {
  return readCookie(DEMO_COOKIE_NAME) === "1";
}

/**
 * Enters demo mode by setting the gate cookie. The cookie is not a real
 * session (API routes still require `bw_session`), it only lets the edge
 * proxy render protected pages in the browser.
 */
export function enterDemoMode(): void {
  if (typeof document === "undefined") return;
  document.cookie = `${DEMO_COOKIE_NAME}=1; path=/; SameSite=Lax; max-age=31536000`;
}

/** Exits demo mode: removes the gate cookie and wipes local demo data. */
export function exitDemoMode(): void {
  if (typeof document === "undefined") return;
  document.cookie = `${DEMO_COOKIE_NAME}=; path=/; SameSite=Lax; max-age=0`;
  sessionStorage.removeItem(STORAGE_KEY);
}

// ---------------------------------------------------------------------------
// Deterministic seed generator (mulberry32 PRNG; originally derived from the
// removed `scripts/seed.ts`)
// ---------------------------------------------------------------------------

/** Deterministic PRNG so every visitor gets the same reproducible dataset. */
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
  mode: MealPayload["entryMode"];
  ingredients: IngredientSeed[];
  totalCalories?: number;
  totalProtein?: number;
  totalCarbs?: number;
  totalFat?: number;
  probability: number;
}

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

const DAYS = 45;
const SEED = 20260823;

function buildMealDto(input: {
  id: string;
  logDate: string;
  payload: MealPayload;
  order: number;
  createdAt: number;
}): StoredMeal {
  const totals = resolveMealTotals(
    input.payload.entryMode,
    input.payload.ingredients,
    input.payload.totalCalories ?? null,
    input.payload.totalProtein ?? null,
    input.payload.totalCarbs ?? null,
    input.payload.totalFat ?? null,
  );
  const isTotalOnly = input.payload.entryMode === "total_only";
  return {
    id: input.id,
    logDate: input.logDate,
    title: input.payload.title,
    notes: input.payload.notes ?? null,
    entryMode: input.payload.entryMode,
    ingredients: input.payload.ingredients,
    totalCalories: isTotalOnly ? (input.payload.totalCalories ?? null) : null,
    totalProtein: isTotalOnly ? (input.payload.totalProtein ?? null) : null,
    totalCarbs: isTotalOnly ? (input.payload.totalCarbs ?? null) : null,
    totalFat: isTotalOnly ? (input.payload.totalFat ?? null) : null,
    resolvedCalories: totals.calories,
    resolvedProtein: totals.protein,
    resolvedCarbs: totals.carbs,
    resolvedFat: totals.fat,
    order: input.order,
    createdAt: input.createdAt,
  };
}

/** Generates the full demo dataset relative to the browser's local today. */
export function buildDemoStore(today: string = todayKey()): DemoStore {
  const rand = mulberry32(SEED);
  const jitter = (value: number, spread: number) =>
    Math.round(value + (rand() - 0.5) * spread);

  const meals: StoredMeal[] = [];
  const weights: WeightDTO[] = [];
  const now = Date.now();
  let sortCounter = 0;

  function pick(seeds: MealSeed[]): MealSeed {
    return seeds[Math.floor(rand() * seeds.length)];
  }

  for (let daysAgo = DAYS; daysAgo >= 0; daysAgo--) {
    const logDate = addDaysToKey(today, -daysAgo);
    const plan: MealSeed[] = [];

    if (rand() < 0.97) plan.push(pick(breakfasts));
    if (rand() < 0.98) plan.push(pick(lunches));
    if (rand() < 0.92) plan.push(pick(dinners));
    if (rand() < 0.55) plan.push(pick(snacks));

    for (const seed of plan) {
      if (seed.probability < 1 && rand() > seed.probability) continue;
      const ingredients: IngredientInput[] = seed.ingredients.map((ingredient) => ({
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
      meals.push(
        buildMealDto({
          id: newId(),
          logDate,
          order: sortCounter++,
          createdAt: now,
          payload: {
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
          },
        }),
      );
    }

    if (rand() < 0.9) {
      const baseWeight = 84 - ((DAYS - daysAgo) / DAYS) * 1.8;
      const weightKg = Math.round((baseWeight + (rand() - 0.5) * 0.7) * 10) / 10;
      const baseBfPct = 19 - ((DAYS - daysAgo) / DAYS) * 3;
      const bodyFatPct =
        rand() < 0.75 ? Math.round((baseBfPct + (rand() - 0.5) * 1.6) * 10) / 10 : null;
      const measuredAt = new Date(`${logDate}T07:45:00`);
      weights.push({
        id: newId(),
        measuredAt: measuredAt.toISOString(),
        weightKg,
        bodyFatPct,
        note: null,
      });
      if (rand() < 0.12) {
        const eveningWeight = Math.round((weightKg + 0.4 + rand() * 0.3) * 10) / 10;
        weights.push({
          id: newId(),
          measuredAt: new Date(`${logDate}T21:30:00`).toISOString(),
          weightKg: eveningWeight,
          bodyFatPct: null,
          note: "tras entrenar",
        });
      }
    }
  }

  const templates: MealTemplateDTO[] = [
    {
      id: newId(),
      name: "Desayuno",
      title: "Desayuno",
      notes: null,
      ingredients: breakfasts[0].ingredients,
    },
    {
      id: newId(),
      name: "Cena ligera",
      title: "Cena ligera",
      notes: null,
      ingredients: [],
    },
  ];

  return { meals, templates, weights };
}

// ---------------------------------------------------------------------------
// Store persistence
// ---------------------------------------------------------------------------

function emptyStore(): DemoStore {
  return { meals: [], templates: [], weights: [] };
}

function loadStore(): DemoStore {
  if (typeof sessionStorage === "undefined") return emptyStore();
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return emptyStore();
  try {
    return JSON.parse(raw) as DemoStore;
  } catch {
    return emptyStore();
  }
}

function saveStore(store: DemoStore): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(store));
}

/** Returns the current store, seeding it on first use. */
export function ensureDemoStore(): DemoStore {
  let store = loadStore();
  if (store.meals.length === 0 && store.templates.length === 0 && store.weights.length === 0) {
    store = buildDemoStore();
    saveStore(store);
  }
  return store;
}

// ---------------------------------------------------------------------------
// Read operations
// ---------------------------------------------------------------------------

function stripMeal(meal: StoredMeal): MealDTO {
  return {
    id: meal.id,
    logDate: meal.logDate,
    title: meal.title,
    notes: meal.notes,
    entryMode: meal.entryMode,
    ingredients: meal.ingredients,
    totalCalories: meal.totalCalories,
    totalProtein: meal.totalProtein,
    totalCarbs: meal.totalCarbs,
    totalFat: meal.totalFat,
    resolvedCalories: meal.resolvedCalories,
    resolvedProtein: meal.resolvedProtein,
    resolvedCarbs: meal.resolvedCarbs,
    resolvedFat: meal.resolvedFat,
  };
}

export function listDemoMeals(): MealDTO[] {
  return ensureDemoStore().meals.map(stripMeal);
}

export function listDemoMealsInRange(from: string | null, to: string | null): MealDTO[] {
  const store = ensureDemoStore();
  return store.meals
    .filter(
      (meal) =>
        (from === null || meal.logDate >= from) && (to === null || meal.logDate <= to),
    )
    .sort((a, b) =>
      a.logDate === b.logDate ? a.order - b.order : a.logDate.localeCompare(b.logDate),
    )
    .map(stripMeal);
}

export function listDemoTemplates(): MealTemplateDTO[] {
  return ensureDemoStore().templates;
}

export function listDemoWeights(): WeightDTO[] {
  return [...ensureDemoStore().weights].sort((a, b) =>
    a.measuredAt.localeCompare(b.measuredAt),
  );
}

// ---------------------------------------------------------------------------
// Write operations
// ---------------------------------------------------------------------------

function write(fn: (store: DemoStore) => DemoStore): void {
  saveStore(fn(loadStore()));
}

export function createDemoMeal(payload: MealPayload): MealDTO {
  const now = Date.now();
  const store = ensureDemoStore();
  const maxOrder = store.meals
    .filter((meal) => meal.logDate === payload.logDate)
    .reduce((max, meal) => Math.max(max, meal.order), -1);
  const meal = buildMealDto({
    id: newId(),
    logDate: payload.logDate,
    payload,
    order: maxOrder + 1,
    createdAt: now,
  });
  write((s) => ({ ...s, meals: [...s.meals, meal] }));
  return stripMeal(meal);
}

export function updateDemoMeal(id: string, payload: MealPayload): MealDTO | null {
  let updated: MealDTO | null = null;
  write((store) => ({
    ...store,
    meals: store.meals.map((meal) => {
      if (meal.id !== id) return meal;
      const built = buildMealDto({
        id,
        logDate: payload.logDate,
        payload,
        order: meal.order,
        createdAt: meal.createdAt,
      });
      updated = stripMeal(built);
      return built;
    }),
  }));
  return updated;
}

export function deleteDemoMeal(id: string): boolean {
  let found = false;
  write((store) => {
    const next = store.meals.filter((meal) => meal.id !== id);
    found = next.length !== store.meals.length;
    return { ...store, meals: next };
  });
  return found;
}

export function reorderDemoMeals(orderedIds: string[]): void {
  write((store) => {
    const byId = new Map(store.meals.map((meal) => [meal.id, meal]));
    const reordered = orderedIds
      .map((id, index) => {
        const meal = byId.get(id);
        return meal ? { ...meal, order: index } : null;
      })
      .filter((meal): meal is StoredMeal => meal !== null);
    const remaining = store.meals.filter((meal) => !orderedIds.includes(meal.id));
    return { ...store, meals: [...remaining, ...reordered] };
  });
}

export function createDemoTemplate(payload: TemplatePayload): MealTemplateDTO {
  const template: MealTemplateDTO = {
    id: newId(),
    name: payload.name,
    title: payload.title,
    notes: payload.notes ?? null,
    ingredients: payload.ingredients,
  };
  write((store) => ({ ...store, templates: [...store.templates, template] }));
  return template;
}

export function deleteDemoTemplate(id: string): boolean {
  let found = false;
  write((store) => {
    const next = store.templates.filter((template) => template.id !== id);
    found = next.length !== store.templates.length;
    return { ...store, templates: next };
  });
  return found;
}

export function createDemoWeight(payload: WeightPayload): WeightDTO {
  const weight: WeightDTO = {
    id: newId(),
    measuredAt: payload.measuredAt,
    weightKg: payload.weightKg,
    bodyFatPct: payload.bodyFatPct ?? null,
    note: payload.note ?? null,
  };
  write((store) => ({
    ...store,
    weights: [...store.weights, weight].sort((a, b) =>
      a.measuredAt.localeCompare(b.measuredAt),
    ),
  }));
  return weight;
}

export function updateDemoWeight(id: string, payload: WeightPayload): WeightDTO | null {
  let updated: WeightDTO | null = null;
  write((store) => ({
    ...store,
    weights: store.weights
      .map((weight) => {
        if (weight.id !== id) return weight;
        updated = {
          ...weight,
          measuredAt: payload.measuredAt,
          weightKg: payload.weightKg,
          bodyFatPct: payload.bodyFatPct ?? null,
          note: payload.note ?? null,
        };
        return updated;
      })
      .sort((a, b) => a.measuredAt.localeCompare(b.measuredAt)),
  }));
  return updated;
}

export function deleteDemoWeight(id: string): boolean {
  let found = false;
  write((store) => {
    const next = store.weights.filter((weight) => weight.id !== id);
    found = next.length !== store.weights.length;
    return { ...store, weights: next };
  });
  return found;
}

// --- Settings ---

export function getDemoSettings(): CalorieProfile {
  return { ...DEMO_CALORIE_PROFILE };
}

export function updateDemoSettings(profile: CalorieProfile): CalorieProfile {
  return { ...profile };
}
