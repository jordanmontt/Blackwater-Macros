import { describe, expect, it, vi, beforeEach } from "vitest";
import {
  createMemoryWorld,
  authenticateWith,
  jsonRequest,
  emptyCalorieProfile,
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

import { PUT as putSettings } from "@/app/api/settings/route";

holder.world = createMemoryWorld();

const world = holder.world;
const USER = "u-1";

function authenticate(): void {
  authenticateWith(holder.authCookie, world!, USER);
}

function settingsRequest(body: unknown): Request {
  return jsonRequest("/api/settings", body, "PUT");
}

describe("ruta de ajustes", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  it("devuelve 401 sin sesión", async () => {
    const res = await putSettings(settingsRequest({ gender: "male" }));
    expect(res.status).toBe(401);
  });

  it("devuelve 400 si el payload no incluye campos de perfil", async () => {
    authenticate();
    const res = await putSettings(settingsRequest({ foo: "bar" }));
    expect(res.status).toBe(400);
    expect(await res.json()).toMatchObject({ code: "invalid_data" });
  });

  it("guarda y devuelve el perfil calórico", async () => {
    authenticate();
    const res = await putSettings(
      settingsRequest({
        gender: "male",
        birthYear: 1990,
        heightCm: 178,
        gymDaysPerWeek: 3,
        gymSessionMinutes: 60,
        walkingMinutesPerDay: 30,
        calorieGoal: "cut",
      }),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.calorieProfile).toEqual({
      gender: "male",
      birthYear: 1990,
      heightCm: 178,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: "cut",
    });
  });

  it("devuelve 400 si un campo no valida", async () => {
    authenticate();
    const res = await putSettings(
      settingsRequest({
        gender: "male",
        birthYear: 1900,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
      }),
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe("birth_year_out_of_range");
  });

  it("permite guardar un perfil vacío", async () => {
    authenticate();
    const res = await putSettings(settingsRequest(emptyCalorieProfile()));
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.calorieProfile.gender).toBeNull();
    expect(body.calorieProfile.calorieGoal).toBeNull();
  });
});