import { buildStatsFromData } from "./core/stats-builder";
import {
  restoreDemoMeal,
  restoreDemoTemplate,
  restoreDemoWeight,
  createDemoMeal,
  createDemoTemplate,
  createDemoWeight,
  deleteDemoMeal,
  deleteDemoTemplate,
  deleteDemoWeight,
  listDemoMeals,
  listDemoMealsInRange,
  listDemoTemplates,
  listDemoWeights,
  reorderDemoMeals,
  updateDemoMeal,
  updateDemoSettings,
  updateDemoTemplate,
  updateDemoWeight,
  DEMO_CALORIE_PROFILE,
  DEMO_USERNAME,
} from "./demo-store";
import type {
  CalorieProfile,
  MealDTO,
  MealTemplateDTO,
  StatsRange,
  StatsSummary,
  WeightDTO,
} from "./core/types";

/**
 * Client-side implementation of the `api` interface backed by the local demo
 * store. Used only while demo mode is active, so every operation stays in the
 * browser session and never touches the network or the database.
 */
export const demoApi = {
  session: async () => ({
    username: DEMO_USERNAME,
    isAdmin: false,
    calorieProfile: { ...DEMO_CALORIE_PROFILE },
  }),

  listMeals: async (from: string, to: string): Promise<MealDTO[]> =>
    listDemoMealsInRange(from, to),

  createMeal: async (payload: Parameters<typeof createDemoMeal>[0]): Promise<MealDTO> =>
    createDemoMeal(payload),

  updateMeal: async (id: string, payload: Parameters<typeof updateDemoMeal>[1]): Promise<MealDTO> => {
    const meal = updateDemoMeal(id, payload);
    if (!meal) throw new Error("Comida no encontrada");
    return meal;
  },

  deleteMeal: async (id: string): Promise<{ ok: true }> => {
    deleteDemoMeal(id);
    return { ok: true };
  },

  restoreMeal: async (meal: MealDTO, orderedIds: string[]): Promise<void> => {
    restoreDemoMeal(meal);
    reorderDemoMeals(orderedIds);
  },

  reorderMeals: async (orderedIds: string[]): Promise<{ ok: true }> => {
    reorderDemoMeals(orderedIds);
    return { ok: true };
  },

  listTemplates: async (): Promise<MealTemplateDTO[]> => listDemoTemplates(),

  createTemplate: async (
    payload: Parameters<typeof createDemoTemplate>[0],
  ): Promise<MealTemplateDTO> => createDemoTemplate(payload),

  updateTemplate: async (
    id: string,
    payload: Parameters<typeof updateDemoTemplate>[1],
  ): Promise<MealTemplateDTO> => {
    const template = updateDemoTemplate(id, payload);
    if (!template) throw new Error("Plantilla no encontrada");
    return template;
  },

  restoreTemplate: async (template: MealTemplateDTO): Promise<void> => restoreDemoTemplate(template),

  deleteTemplate: async (id: string): Promise<{ ok: true }> => {
    deleteDemoTemplate(id);
    return { ok: true };
  },

  listWeights: async (): Promise<WeightDTO[]> => listDemoWeights(),

  createWeight: async (
    payload: Parameters<typeof createDemoWeight>[0],
  ): Promise<WeightDTO> => createDemoWeight(payload),

  updateWeight: async (id: string, payload: Parameters<typeof updateDemoWeight>[1]): Promise<WeightDTO> => {
    const weight = updateDemoWeight(id, payload);
    if (!weight) throw new Error("Registro no encontrado");
    return weight;
  },

  restoreWeight: async (weight: WeightDTO): Promise<void> => restoreDemoWeight(weight),

  deleteWeight: async (id: string): Promise<{ ok: true }> => {
    deleteDemoWeight(id);
    return { ok: true };
  },

  stats: async (range: StatsRange, today: string): Promise<StatsSummary> =>
    buildStatsFromData(listDemoMeals(), listDemoWeights().map((w) => ({
      measuredAt: new Date(w.measuredAt),
      weightKg: w.weightKg,
      bodyFatPct: w.bodyFatPct,
    })), range, today),

  updateSettings: async (profile: CalorieProfile) => {
    const updated = updateDemoSettings(profile);
    return { calorieProfile: updated };
  },
};
