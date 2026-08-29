import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  createMemoryWorld,
  authenticateWith,
  templateRow,
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

import {
  GET as getTemplates,
  POST as postTemplate,
} from "@/app/api/templates/route";
import {
  PATCH as patchTemplate,
  DELETE as deleteTemplate,
} from "@/app/api/templates/[id]/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

describe("rutas de plantillas", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  describe("sin sesión", () => {
    it("GET y POST devuelven 401", async () => {
      expect((await getTemplates()).status).toBe(401);
      expect((await postTemplate(jsonRequest("/api/templates", {}))).status).toBe(401);
    });
  });

  describe("POST /api/templates", () => {
    it("crea una plantilla por ingredientes", async () => {
      authenticate();
      const res = await postTemplate(
        jsonRequest("/api/templates", {
          name: "Desayuno base",
          title: "Avena con leche",
          entryMode: "per_ingredient",
          ingredients: [
            { name: "Avena", calories: 150, protein: 5 },
            { name: "Leche", calories: 100, protein: 3.5 },
          ],
        }),
      );
      expect(res.status).toBe(201);
      const { template } = await res.json();
      expect(template.name).toBe("Desayuno base");
      expect(template.entryMode).toBe("per_ingredient");
      expect(template.resolvedCalories).toBe(250);
      expect(world!.data.templates.has(template.id)).toBe(true);
    });

    it("crea una plantilla con totales declarados", async () => {
      authenticate();
      const res = await postTemplate(
        jsonRequest("/api/templates", {
          name: "Batido",
          title: "Batido proteico",
          entryMode: "total_only",
          ingredients: [],
          totalCalories: 400,
          totalProtein: 30,
        }),
      );
      expect(res.status).toBe(201);
      const { template } = await res.json();
      expect(template.totalCalories).toBe(400);
      expect(template.resolvedProtein).toBe(30);
    });

    it("devuelve 400 sin nombre de plantilla", async () => {
      authenticate();
      const res = await postTemplate(
        jsonRequest("/api/templates", {
          title: "Sin nombre",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
      );
      expect(res.status).toBe(400);
      const body = await res.json();
      expect(["El nombre de la plantilla es obligatorio", "Invalid input: expected string, received undefined"]).toContain(body.error);
    });
  });

  describe("GET /api/templates", () => {
    it("devuelve las plantillas ordenadas por nombre", async () => {
      authenticate();
      await postTemplate(
        jsonRequest("/api/templates", {
          name: "Zeta",
          title: "Z",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
      );
      await postTemplate(
        jsonRequest("/api/templates", {
          name: "Alfa",
          title: "A",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
      );
      const res = await getTemplates();
      expect(res.status).toBe(200);
      const { templates } = await res.json();
      expect(templates.map((t: { name: string }) => t.name)).toEqual(["Alfa", "Zeta"]);
    });
  });

  describe("PATCH /api/templates/[id]", () => {
    it("actualiza una plantilla propia", async () => {
      authenticate();
      const created = await postTemplate(
        jsonRequest("/api/templates", {
          name: "Antes",
          title: "Antes",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
      );
      const { template: createdTemplate } = await created.json();

      const res = await patchTemplate(
        jsonRequest("/api/templates/x", {
          name: "Después",
          title: "Después",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
        routeParams(createdTemplate.id),
      );
      expect(res.status).toBe(200);
      const { template } = await res.json();
      expect(template.name).toBe("Después");
    });

    it("devuelve 404 si la plantilla es de otro usuario", async () => {
      authenticate();
      const res = await patchTemplate(
        jsonRequest("/api/templates/x", {
          name: "X",
          title: "X",
          entryMode: "per_ingredient",
          ingredients: [],
        }),
        routeParams("t-otra"),
      );
      expect(res.status).toBe(404);
      expect(await res.json()).toEqual({ error: "Plantilla no encontrada" });
    });
  });

  describe("DELETE /api/templates/[id]", () => {
    it("borra una plantilla propia", async () => {
      authenticate();
      world!.data.templates.set("t-1", templateRow({ id: "t-1", userId: USER, name: "A", title: "A" }));
      const res = await deleteTemplate(
        new Request("http://test/api/templates/t-1"),
        routeParams("t-1"),
      );
      expect(res.status).toBe(200);
      expect(world!.data.templates.has("t-1")).toBe(false);
    });

    it("devuelve 404 al borrar la plantilla de otro usuario", async () => {
      authenticate();
      const res = await deleteTemplate(
        new Request("http://test/api/templates/t-otra"),
        routeParams("t-otra"),
      );
      expect(res.status).toBe(404);
    });
  });
});