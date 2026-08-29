import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  createMemoryWorld,
  authenticateWith,
  mealRow,
  jsonRequest,
  routeParams,
} from "../helpers/repos";
import type { MemoryWorld } from "../helpers/repos";

const holder = vi.hoisted(() => ({
  world: null as MemoryWorld | null,
  authCookie: { value: "" },
}));

vi.mock("@/server/composition", () => ({
  get repositories() {
    return holder.world?.repositories;
  },
  get serviceDeps() {
    return holder.world?.serviceDeps;
  },
}));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      name === "bw_session" && holder.authCookie.value
        ? { name, value: holder.authCookie.value }
        : undefined,
  }),
}));

import { GET as getMeals, POST as postMeal } from "@/app/api/meals/route";
import { PATCH as reorderMeals } from "@/app/api/meals/reorder/route";
import { PATCH as patchMeal, DELETE as deleteMeal } from "@/app/api/meals/[id]/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";
const OTHER = "u-otro";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

describe("rutas de comidas", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  describe("sin sesión", () => {
    it("GET y POST devuelven 401", async () => {
      expect((await getMeals(new Request("http://test/api/meals"))).status).toBe(401);
      expect((await postMeal(jsonRequest("/api/meals", {}))).status).toBe(401);
    });
  });

  describe("GET /api/meals", () => {
    it("devuelve la lista vacía al principio", async () => {
      authenticate();
      const res = await getMeals(new Request("http://test/api/meals"));
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual({ meals: [] });
    });

    it("filtra por rango de fechas y no filtra sin parámetros", async () => {
      authenticate();
      world!.data.meals.set("m-1", mealRow({ id: "m-1", userId: USER, logDate: "2026-06-14" }));
      world!.data.meals.set("m-2", mealRow({ id: "m-2", userId: USER, logDate: "2026-06-20" }));

      const all = await getMeals(new Request("http://test/api/meals"));
      const allBody = await all.json();
      expect(allBody.meals.map((meal: { id: string }) => meal.id)).toEqual(["m-1", "m-2"]);

      const ranged = await getMeals(new Request("http://test/api/meals?from=2026-06-15"));
      const rangedBody = await ranged.json();
      expect(rangedBody.meals.map((meal: { id: string }) => meal.id)).toEqual(["m-2"]);

      const day = await getMeals(
        new Request("http://test/api/meals?from=2026-06-20&to=2026-06-20"),
      );
      const dayBody = await day.json();
      expect(dayBody.meals.map((meal: { id: string }) => meal.id)).toEqual(["m-2"]);
    });
  });

  describe("POST /api/meals", () => {
    it("crea una comida por ingredientes y calcula los totales", async () => {
      authenticate();
      const res = await postMeal(
        jsonRequest("/api/meals", {
          logDate: "2026-06-15",
          title: "Desayuno",
          entryMode: "per_ingredient",
          ingredients: [
            { name: "Avena", calories: 150, protein: 5, carbs: 27, fat: 3 },
            { name: "Leche", calories: 100, protein: 3.5, carbs: 5, fat: 7 },
          ],
        }),
      );
      expect(res.status).toBe(201);
      const { meal } = await res.json();
      expect(meal.title).toBe("Desayuno");
      expect(meal.entryMode).toBe("per_ingredient");
      expect(meal.totalCalories).toBeNull();
      expect(meal.resolvedCalories).toBe(250);
      expect(meal.resolvedProtein).toBeCloseTo(8.5);
      expect(meal.resolvedCarbs).toBe(32);
      expect(meal.resolvedFat).toBe(10);
      expect(world!.data.meals.has(meal.id)).toBe(true);
    });

    it("crea una comida con totales declarados", async () => {
      authenticate();
      const res = await postMeal(
        jsonRequest("/api/meals", {
          logDate: "2026-06-15",
          title: "Batido",
          entryMode: "total_only",
          ingredients: [],
          totalCalories: 700,
          totalProtein: 35,
          totalCarbs: 90,
          totalFat: 20,
        }),
      );
      expect(res.status).toBe(201);
      const { meal } = await res.json();
      expect(meal.entryMode).toBe("total_only");
      expect(meal.totalCalories).toBe(700);
      expect(meal.resolvedCalories).toBe(700);
      expect(meal.resolvedProtein).toBe(35);
    });

    it("devuelve 400 si el payload no valida", async () => {
      authenticate();
      const res = await postMeal(
        jsonRequest("/api/meals", {
          logDate: "2026-06-15",
          title: "",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
      );
      expect(res.status).toBe(400);
      const body = await res.json();
      expect(body.error).toBe("El título es obligatorio");
    });

    it("devuelve 400 si la fecha no es válida", async () => {
      authenticate();
      const res = await postMeal(
        jsonRequest("/api/meals", {
          logDate: "15/06/2026",
          title: "Comida",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
      );
      expect(res.status).toBe(400);
    });
  });

  describe("PATCH /api/meals/[id]", () => {
    it("actualiza una comida propia", async () => {
      authenticate();
      const created = await postMeal(
        jsonRequest("/api/meals", {
          logDate: "2026-06-15",
          title: "Antes",
          entryMode: "total_only",
          ingredients: [],
          totalCalories: 500,
        }),
      );
      const { meal: createdMeal } = await created.json();

      const res = await patchMeal(
        jsonRequest("/api/meals/mal", {
          logDate: "2026-06-15",
          title: "Después",
          entryMode: "total_only",
          ingredients: [],
          totalCalories: 600,
        }),
        routeParams(createdMeal.id),
      );
      expect(res.status).toBe(200);
      const { meal } = await res.json();
      expect(meal.id).toBe(createdMeal.id);
      expect(meal.title).toBe("Después");
      expect(meal.resolvedCalories).toBe(600);
    });

    it("devuelve 404 si la comida es de otro usuario", async () => {
      authenticate();
      world!.data.meals.set("m-otro", mealRow({ id: "m-otro", userId: OTHER }));
      const res = await patchMeal(
        jsonRequest("/api/meals/x", {
          logDate: "2026-06-15",
          title: "Algo",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
        routeParams("m-otro"),
      );
      expect(res.status).toBe(404);
      expect(await res.json()).toEqual({ error: "Comida no encontrada" });
    });
  });

  describe("DELETE /api/meals/[id]", () => {
    it("borra una comida propia y deja de aparecer en la lista", async () => {
      authenticate();
      world!.data.meals.set("m-1", mealRow({ id: "m-1", userId: USER }));
      const res = await deleteMeal(new Request("http://test/api/meals/m-1"), routeParams("m-1"));
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual({ ok: true });
      expect(world!.data.meals.has("m-1")).toBe(false);
    });

    it("devuelve 404 al borrar la comida de otro usuario", async () => {
      authenticate();
      world!.data.meals.set("m-otro", mealRow({ id: "m-otro", userId: OTHER }));
      const res = await deleteMeal(
        new Request("http://test/api/meals/m-otro"),
        routeParams("m-otro"),
      );
      expect(res.status).toBe(404);
    });
  });

  describe("PATCH /api/meals/reorder", () => {
    it("reordena las comidas del mismo día", async () => {
      authenticate();
      world!.data.meals.set(
        "aaaaaaaa-0000-4000-8000-000000000001",
        mealRow({
          id: "aaaaaaaa-0000-4000-8000-000000000001",
          userId: USER,
          sortOrder: 0,
        }),
      );
      world!.data.meals.set(
        "aaaaaaaa-0000-4000-8000-000000000002",
        mealRow({
          id: "aaaaaaaa-0000-4000-8000-000000000002",
          userId: USER,
          sortOrder: 1,
        }),
      );

      const res = await reorderMeals(
        jsonRequest("/api/meals/reorder", {
          orderedIds: [
            "aaaaaaaa-0000-4000-8000-000000000002",
            "aaaaaaaa-0000-4000-8000-000000000001",
          ],
        }),
      );
      expect(res.status).toBe(200);

      const list = await getMeals(new Request("http://test/api/meals"));
      const { meals } = await list.json();
      expect(meals.map((meal: { id: string }) => meal.id)).toEqual([
        "aaaaaaaa-0000-4000-8000-000000000002",
        "aaaaaaaa-0000-4000-8000-000000000001",
      ]);
    });
  });
});