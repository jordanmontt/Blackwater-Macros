import { describe, expect, it } from "vitest";
import {
  createMeal,
  deleteMeal,
  listMealsInRange,
  reorderMeals,
  updateMeal,
} from "@/server/services/meals-service";
import {
  createTemplate,
  deleteTemplate,
  listTemplates,
  updateTemplate,
} from "@/server/services/templates-service";
import type { NewMealData, MealsRepository } from "@/server/repositories/meals-repo";
import type {
  MealTemplatesRepository,
  NewTemplateData,
} from "@/server/repositories/templates-repo";
import type { MealInput, TemplateInput } from "@/server/validation";
import type { MealIngredient, MealRow, MealTemplateRow } from "@/server/db/schema";

/**
 * Requisitos de gestión de comidas:
 *  - se pueden editar y borrar comidas ya registradas,
 *  - los cambios se reflejan inmediatamente en lo que se vuelve a leer,
 *  - cada usuario solo ve sus propias comidas,
 *  - las plantillas guardan una comida para reutilizarla otro día.
 */

function memoryMeals(): MealsRepository {
  const rows = new Map<string, MealRow>();
  let nextId = 1;

  return {
    async listInRange(userId, from, to) {
      return [...rows.values()]
        .filter(
          (row) =>
            row.userId === userId &&
            (from === null || row.logDate >= from) &&
            (to === null || row.logDate <= to),
        )
        .sort((a, b) => a.logDate.localeCompare(b.logDate) || a.sortOrder - b.sortOrder);
    },
    async getById(userId, id) {
      const found = rows.get(id);
      return found && found.userId === userId ? found : null;
    },
    async create(userId, data: NewMealData) {
      const id = `meal-${nextId++}`;
      const now = new Date();
      const existingForDay = [...rows.values()].filter(
        (r) => r.userId === userId && r.logDate === data.logDate,
      );
      const sortOrder =
        existingForDay.length > 0
          ? Math.max(...existingForDay.map((r) => r.sortOrder)) + 1
          : 0;
      const row: MealRow = { id, userId, createdAt: now, updatedAt: now, sortOrder, ...data };
      rows.set(id, row);
      return row;
    },
    async update(userId, id, data: NewMealData) {
      const existing = rows.get(id);
      if (!existing || existing.userId !== userId) return null;
      const updated: MealRow = { ...existing, ...data };
      rows.set(id, updated);
      return updated;
    },
    async delete(userId, id) {
      const existing = rows.get(id);
      if (!existing || existing.userId !== userId) return false;
      rows.delete(id);
      return true;
    },
    async reorder(userId, orderedIds) {
      for (let i = 0; i < orderedIds.length; i++) {
        const row = rows.get(orderedIds[i]);
        if (row && row.userId === userId) {
          rows.set(orderedIds[i], { ...row, sortOrder: i });
        }
      }
    },
  };
}

function memoryTemplates(): MealTemplatesRepository {
  const rows = new Map<string, MealTemplateRow>();
  let nextId = 1;

  return {
    async listForUser(userId) {
      return [...rows.values()]
        .filter((row) => row.userId === userId)
        .sort((a, b) => a.name.localeCompare(b.name));
    },
    async getById(userId, id) {
      const found = rows.get(id);
      return found && found.userId === userId ? found : null;
    },
    async create(userId, data: NewTemplateData) {
      const id = `tpl-${nextId++}`;
      const row: MealTemplateRow = {
        id,
        userId,
        createdAt: new Date(),
        ...data,
      };
      rows.set(id, row);
      return row;
    },
    async update(userId, id, data: NewTemplateData) {
      const existing = rows.get(id);
      if (!existing || existing.userId !== userId) return null;
      const updated: MealTemplateRow = { ...existing, ...data };
      rows.set(id, updated);
      return updated;
    },
    async delete(userId, id) {
      const existing = rows.get(id);
      if (!existing || existing.userId !== userId) return false;
      rows.delete(id);
      return true;
    },
  };
}

const breakfast: MealInput = {
  logDate: "2026-08-23",
  title: "Desayuno",
  notes: "café solo",
  entryMode: "per_ingredient",
  ingredients: [{ name: "4 huevos", quantity: "240 g", calories: 280, protein: 24 }],
  totalCalories: null,
  totalProtein: null,
};

describe("editar y borrar comidas", () => {
  it("los cambios al editar una comida se ven inmediatamente al volver a listar", async () => {
    const deps = memoryMeals();
    const created = await createMeal(deps, "user-1", breakfast);

    await updateMeal(deps, "user-1", created.id, {
      ...breakfast,
      title: "Desayuno tardío",
      ingredients: [...breakfast.ingredients, { name: "pan", calories: 90, protein: 3 }],
    });

    const [visible] = await listMealsInRange(deps, "user-1", "2026-08-23", "2026-08-23");
    expect(visible.title).toBe("Desayuno tardío");
    expect(visible.resolvedCalories).toBe(370);
  });

  it("borrar una comida hace desaparecer el día como si no hubiera existido", async () => {
    const deps = memoryMeals();
    const created = await createMeal(deps, "user-1", breakfast);

    await deleteMeal(deps, "user-1", created.id);
    const visible = await listMealsInRange(deps, "user-1", "2026-08-01", "2026-08-31");

    expect(visible).toEqual([]);
  });

  it("nadie puede editar ni borrar las comidas de otro usuario", async () => {
    const deps = memoryMeals();
    const created = await createMeal(deps, "user-1", breakfast);

    await expect(updateMeal(deps, "user-2", created.id, breakfast)).resolves.toBeNull();
    await expect(deleteMeal(deps, "user-2", created.id)).resolves.toBe(false);
    // la comida sigue intacta para su dueño
    const own = await listMealsInRange(deps, "user-1", null, null);
    expect(own).toHaveLength(1);
  });
});

describe("reordenar comidas", () => {
  it("una nueva comida se añade al final del día", async () => {
    const deps = memoryMeals();
    await createMeal(deps, "user-1", breakfast);
    await createMeal(deps, "user-1", {
      ...breakfast,
      title: "Almuerzo",
      logDate: "2026-08-23",
    });

    const listed = await listMealsInRange(deps, "user-1", "2026-08-23", "2026-08-23");
    expect(listed.map((m) => m.title)).toEqual(["Desayuno", "Almuerzo"]);
  });

  it("comidas en días distintos empiezan al final de su día", async () => {
    const deps = memoryMeals();
    await createMeal(deps, "user-1", breakfast);
    await createMeal(deps, "user-1", {
      ...breakfast,
      title: "Comida otro día",
      logDate: "2026-08-24",
    });

    const day1 = await listMealsInRange(deps, "user-1", "2026-08-23", "2026-08-23");
    const day2 = await listMealsInRange(deps, "user-1", "2026-08-24", "2026-08-24");
    expect(day1).toHaveLength(1);
    expect(day2).toHaveLength(1);
    expect(day1[0].title).toBe("Desayuno");
    expect(day2[0].title).toBe("Comida otro día");
  });

  it("reorder cambia el orden según el orden dado", async () => {
    const deps = memoryMeals();
    const meal1 = await createMeal(deps, "user-1", breakfast);
    const meal2 = await createMeal(deps, "user-1", {
      ...breakfast,
      title: "Almuerzo",
    });
    const meal3 = await createMeal(deps, "user-1", {
      ...breakfast,
      title: "Snack",
    });

    await reorderMeals(deps, "user-1", [meal3.id, meal1.id, meal2.id]);

    const listed = await listMealsInRange(deps, "user-1", "2026-08-23", "2026-08-23");
    expect(listed.map((m) => m.id)).toEqual([meal3.id, meal1.id, meal2.id]);
  });

  it("reorder no afecta comidas de otros usuarios", async () => {
    const deps = memoryMeals();
    const user1Meal = await createMeal(deps, "user-1", breakfast);
    await createMeal(deps, "user-2", {
      ...breakfast,
      title: "Comida user-2",
    });

    await reorderMeals(deps, "user-1", [user1Meal.id]);
    const user2Listed = await listMealsInRange(deps, "user-2", "2026-08-23", "2026-08-23");
    expect(user2Listed).toHaveLength(1);
  });
});

describe("plantillas de comidas", () => {
  it("guardar 'Desayuno' como plantilla permite reutilizarlo cualquier día", async () => {
    const deps = memoryTemplates();

    const template = await createTemplate(deps, "user-1", {
      name: "Desayuno",
      title: "Desayuno",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: breakfast.ingredients,
    } satisfies TemplateInput);

    const available = await listTemplates(deps, "user-1");
    expect(available).toHaveLength(1);
    expect(available[0].name).toBe(template.name);

    // Aplicar la plantilla un día cualquiera = crear una comida con sus datos.
    const applied: MealInput = {
      logDate: "2026-09-01",
      title: template.title,
      notes: template.notes,
      entryMode: "per_ingredient",
      ingredients: template.ingredients.map(
        ({ name, quantity, calories, protein }: MealIngredient) => ({
          name,
          quantity,
          calories,
          protein,
        }),
      ),
      totalCalories: null,
      totalProtein: null,
    };
    expect(applied.ingredients[0].name).toBe("4 huevos");
  });

  it("una plantilla 'solo total' guarda y recupera sus macros totales", async () => {
    const deps = memoryTemplates();

    await createTemplate(deps, "user-1", {
      name: "Cena ligera",
      title: "Cena ligera",
      notes: "fuera de casa",
      entryMode: "total_only",
      ingredients: [],
      totalCalories: 380,
      totalProtein: 30,
      totalCarbs: 30,
      totalFat: 15,
    });

    const [available] = await listTemplates(deps, "user-1");
    expect(available.entryMode).toBe("total_only");
    expect(available.resolvedCalories).toBe(380);
    expect(available.resolvedProtein).toBe(30);
    expect(available.resolvedCarbs).toBe(30);
    expect(available.resolvedFat).toBe(15);
  });

  it("una plantilla por ingredientes calcula sus totales resueltos", async () => {
    const deps = memoryTemplates();

    const template = await createTemplate(deps, "user-1", {
      name: "Desayuno",
      title: "Desayuno",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [
        { name: "4 huevos", calories: 280, protein: 24, carbs: 2, fat: 20 },
        { name: "pan", calories: 90, protein: 3, carbs: 16, fat: 1 },
      ],
    });

    expect(template.resolvedCalories).toBe(370);
    expect(template.resolvedProtein).toBe(27);
  });

  it("editar una plantilla actualiza sus datos y totales", async () => {
    const deps = memoryTemplates();
    const template = await createTemplate(deps, "user-1", {
      name: "Desayuno",
      title: "Desayuno",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: breakfast.ingredients,
    });

    const updated = await updateTemplate(deps, "user-1", template.id, {
      name: "Desayuno ampliado",
      title: "Desayuno ampliado",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [
        ...breakfast.ingredients,
        { name: "pan", calories: 90, protein: 3, carbs: 16, fat: 1 },
      ],
    });

    expect(updated).not.toBeNull();
    expect(updated!.title).toBe("Desayuno ampliado");
    expect(updated!.resolvedCalories).toBe(370);
    expect(updated!.resolvedProtein).toBe(27);
  });

  it("cada usuario gestiona sus propias plantillas", async () => {
    const deps = memoryTemplates();

    const template = await createTemplate(deps, "user-1", {
      name: "Desayuno",
      title: "Desayuno",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [],
    });

    expect(await listTemplates(deps, "user-2")).toEqual([]);

    await expect(updateTemplate(deps, "user-2", template.id, {
      name: "X",
      title: "X",
      notes: null,
      entryMode: "per_ingredient",
      ingredients: [],
    })).resolves.toBeNull();

    await deleteTemplate(deps, "user-2", template.id); // intento ajeno
    expect(await listTemplates(deps, "user-1")).toHaveLength(1);

    await deleteTemplate(deps, "user-1", template.id); // borrado legítimo
    expect(await listTemplates(deps, "user-1")).toEqual([]);
  });
});
