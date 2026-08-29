import { describe, expect, it, beforeEach } from "vitest";
import {
  DEMO_COOKIE_NAME,
  buildDemoStore,
  enterDemoMode,
  exitDemoMode,
  isDemoMode,
  createDemoMeal,
  updateDemoMeal,
  deleteDemoMeal,
  listDemoMealsInRange,
  listDemoWeights,
  createDemoWeight,
  DEMO_CALORIE_PROFILE,
  getDemoSettings,
} from "@/lib/demo-store";
import type { MealPayload } from "@/lib/api";

/**
 * Requisitos del modo demo local:
 *  - se entra solo con el navegador (cookie + sessionStorage), sin credenciales,
 *  - el dataset de ejemplo está poblado y anclado al día actual (el día de hoy
 *    no está vacío),
 *  - se puede añadir/editar/borrar datos y todo queda en la sesión,
 *  - al salir del modo demo se limpia la cookie y los datos locales.
 */

const TODAY = "2026-08-28";

function mealPayload(partial: Partial<MealPayload> = {}): MealPayload {
  return {
    logDate: TODAY,
    title: "Comida de prueba",
    notes: null,
    entryMode: "per_ingredient",
    ingredients: [
      { name: "Pollo", quantity: "150 g", calories: 300, protein: 55, carbs: 0, fat: 7 },
    ],
    ...partial,
  };
}

describe("modo demo local", () => {
  beforeEach(() => {
    exitDemoMode();
    sessionStorage.clear();
  });

  it("entrar en demo marca la cookie y salir la limpia", () => {
    expect(isDemoMode()).toBe(false);
    enterDemoMode();
    expect(isDemoMode()).toBe(true);
    expect(document.cookie).toContain(`${DEMO_COOKIE_NAME}=1`);
    exitDemoMode();
    expect(isDemoMode()).toBe(false);
    expect(document.cookie).not.toContain(`${DEMO_COOKIE_NAME}=1`);
  });

  it("el dataset de ejemplo está poblado y ancla el día de hoy (no queda vacío)", () => {
    const store = buildDemoStore(TODAY);
    expect(store.meals.length).toBeGreaterThan(0);
    expect(store.weights.length).toBeGreaterThan(0);
    expect(store.templates.length).toBe(2);
    expect(store.meals.some((meal) => meal.logDate === TODAY)).toBe(true);
  });

  it("las plantillas de ejemplo llevan sus macros resueltas", () => {
    const store = buildDemoStore(TODAY);
    const cenaLigera = store.templates.find((template) => template.name === "Cena ligera");
    expect(cenaLigera).toBeDefined();
    expect(cenaLigera!.entryMode).toBe("total_only");
    expect(cenaLigera!.resolvedCalories).toBe(380);
    expect(cenaLigera!.resolvedProtein).toBe(30);
    expect(cenaLigera!.resolvedCarbs).toBe(30);
    expect(cenaLigera!.resolvedFat).toBe(15);

    const desayuno = store.templates.find((template) => template.name === "Desayuno");
    expect(desayuno!.entryMode).toBe("per_ingredient");
    expect(desayuno!.resolvedCalories).toBeGreaterThan(0);
    expect(desayuno!.resolvedProtein).toBeGreaterThan(0);
  });

  it("el dataset es reproducible con la misma semilla", () => {
    const a = buildDemoStore(TODAY);
    const b = buildDemoStore(TODAY);
    expect(a.meals.length).toBe(b.meals.length);
    expect(a.weights.length).toBe(b.weights.length);
    for (let i = 0; i < a.meals.length; i++) {
      expect(a.meals[i].title).toBe(b.meals[i].title);
      expect(a.meals[i].resolvedCalories).toBe(b.meals[i].resolvedCalories);
    }
  });

  it("crear una comida computa los totales resueltos (modo por ingrediente)", () => {
    enterDemoMode();
    const meal = createDemoMeal(mealPayload());
    expect(meal.resolvedCalories).toBe(300);
    expect(meal.resolvedProtein).toBe(55);
    const found = listDemoMealsInRange(TODAY, TODAY).find((m) => m.id === meal.id);
    expect(found).toBeTruthy();
  });

  it("editar una comida actualiza sus valores", () => {
    enterDemoMode();
    const meal = createDemoMeal(mealPayload());
    const updated = updateDemoMeal(meal.id, mealPayload({ title: "Renombrada" }));
    expect(updated?.title).toBe("Renombrada");
    expect(updated?.id).toBe(meal.id);
  });

  it("borrar una comida la elimina de la sesión", () => {
    enterDemoMode();
    const meal = createDemoMeal(mealPayload());
    expect(deleteDemoMeal(meal.id)).toBe(true);
    expect(listDemoMealsInRange(TODAY, TODAY).find((m) => m.id === meal.id)).toBeUndefined();
  });

  it("registrar peso y recuperarlo (ordenado cronológicamente)", () => {
    enterDemoMode();
    const created = createDemoWeight({
      measuredAt: new Date(`${TODAY}T10:00:00`).toISOString(),
      weightKg: 82.4,
      bodyFatPct: 17.5,
      note: null,
    });
    expect(created.weightKg).toBe(82.4);
    const weights = listDemoWeights();
    expect(weights.some((w) => w.id === created.id)).toBe(true);
  });

  it("el perfil de calorías de demo está definido", () => {
    enterDemoMode();
    const settings = getDemoSettings();
    expect(settings).toEqual(DEMO_CALORIE_PROFILE);
    expect(settings.calorieGoal).toBe("cut");
  });

  it("al salir se limpian los datos locales de la sesión", () => {
    enterDemoMode();
    createDemoMeal(mealPayload());
    exitDemoMode();
    expect(sessionStorage.length).toBe(0);
  });
});
