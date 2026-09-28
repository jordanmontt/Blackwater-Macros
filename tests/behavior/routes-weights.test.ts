import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  createMemoryWorld,
  authenticateWith,
  weightRow,
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

import { GET as getWeights, POST as postWeight } from "@/app/api/weights/route";
import {
  PATCH as patchWeight,
  DELETE as deleteWeight,
} from "@/app/api/weights/[id]/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";
const OTHER = "u-otro";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

describe("rutas de peso", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  describe("sin sesión", () => {
    it("GET y POST devuelven 401", async () => {
      expect((await getWeights(new Request("http://test/api/weights"))).status).toBe(401);
      expect((await postWeight(jsonRequest("/api/weights", {}))).status).toBe(401);
    });
  });

  describe("POST /api/weights", () => {
    it("registra un pesaje", async () => {
      authenticate();
      const res = await postWeight(
        jsonRequest("/api/weights", {
          measuredAt: "2026-06-15T08:00:00.000Z",
          weightKg: 77.4,
        }),
      );
      expect(res.status).toBe(201);
      const { weight } = await res.json();
      expect(weight.weightKg).toBe(77.4);
      expect(weight.bodyFatPct).toBeNull();
      expect(new Date(weight.measuredAt).toISOString()).toBe("2026-06-15T08:00:00.000Z");
    });

    it("guarda el porcentaje de grasa corporal", async () => {
      authenticate();
      const res = await postWeight(
        jsonRequest("/api/weights", {
          measuredAt: "2026-06-15T08:00:00.000Z",
          weightKg: 77.4,
          bodyFatPct: 18.5,
        }),
      );
      expect(res.status).toBe(201);
      const { weight } = await res.json();
      expect(weight.bodyFatPct).toBe(18.5);
    });

    it("devuelve 400 si el peso está fuera de rango", async () => {
      authenticate();
      const res = await postWeight(
        jsonRequest("/api/weights", {
          measuredAt: "2026-06-15T08:00:00.000Z",
          weightKg: 5,
        }),
      );
      expect(res.status).toBe(400);
      const body = await res.json();
      expect(body.code).toBe("weight_out_of_range");
    });
  });

  describe("GET /api/weights", () => {
    it("devuelve los pesajes ordenados por fecha", async () => {
      authenticate();
      world!.data.weights.set(
      "w-1",
      weightRow({ id: "w-1", userId: USER, measuredAt: new Date("2026-06-10T08:00:00Z"), weightKg: 80 }),
    );
      world!.data.weights.set(
      "w-2",
      weightRow({ id: "w-2", userId: USER, measuredAt: new Date("2026-06-15T08:00:00Z"), weightKg: 79.5 }),
    );
      const res = await getWeights(new Request("http://test/api/weights"));
      expect(res.status).toBe(200);
      const { weights } = await res.json();
      expect(weights.map((w: { weightKg: number }) => w.weightKg)).toEqual([80, 79.5]);
    });
  });

  describe("PATCH /api/weights/[id]", () => {
    it("actualiza un pesaje propio", async () => {
      authenticate();
      world!.data.weights.set(
        "w-1",
        weightRow({
          id: "w-1",
          userId: USER,
          measuredAt: new Date("2026-06-15T08:00:00Z"),
          weightKg: 78,
        }),
      );
      const res = await patchWeight(
        jsonRequest("/api/weights/x", {
          measuredAt: "2026-06-15T09:00:00.000Z",
          weightKg: 77.8,
        }),
        routeParams("w-1"),
      );
      expect(res.status).toBe(200);
      const { weight } = await res.json();
      expect(weight.weightKg).toBe(77.8);
    });

    it("devuelve 404 si el pesaje es de otro usuario", async () => {
      authenticate();
      world!.data.weights.set(
        "w-otro",
        weightRow({
          id: "w-otro",
          userId: OTHER,
          measuredAt: new Date("2026-06-15T08:00:00Z"),
          weightKg: 70,
        }),
      );
      const res = await patchWeight(
        jsonRequest("/api/weights/x", {
          measuredAt: "2026-06-15T09:00:00.000Z",
          weightKg: 69.5,
        }),
        routeParams("w-otro"),
      );
      expect(res.status).toBe(404);
      expect(await res.json()).toMatchObject({ code: "weight_not_found" });
    });
  });

  describe("DELETE /api/weights/[id]", () => {
    it("borra un pesaje propio", async () => {
      authenticate();
      world!.data.weights.set(
        "w-1",
        weightRow({
          id: "w-1",
          userId: USER,
          measuredAt: new Date("2026-06-15T08:00:00Z"),
          weightKg: 78,
        }),
      );
      const res = await deleteWeight(
        new Request("http://test/api/weights/w-1"),
        routeParams("w-1"),
      );
      expect(res.status).toBe(200);
      expect(world!.data.weights.has("w-1")).toBe(false);
    });

    it("devuelve 404 al borrar el pesaje de otro usuario", async () => {
      authenticate();
      const res = await deleteWeight(
        new Request("http://test/api/weights/w-otro"),
        routeParams("w-otro"),
      );
      expect(res.status).toBe(404);
    });
  });
});