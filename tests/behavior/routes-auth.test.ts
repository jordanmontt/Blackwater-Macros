import { describe, expect, it, vi, beforeEach } from "vitest";
import { createMemoryWorld, seedUser, seedSession, extractSessionToken } from "../helpers/repos";
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

import { POST as postLogin } from "@/app/api/auth/login/route";
import { POST as postLogout } from "@/app/api/auth/logout/route";
import { GET as getSession } from "@/app/api/auth/session/route";

holder.world = createMemoryWorld();

const world = holder.world;

/** POST /api/auth/login con cuerpo JSON. */
function loginRequest(body: unknown): Request {
  return new Request("http://test/api/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

describe("rutas de autenticación", () => {
  beforeEach(() => {
    world?.reset();
    holder.authCookie.value = "";
  });

  describe("POST /api/auth/login", () => {
    it("devuelve 401 si las credenciales son incorrectas", async () => {
      await seedUser(world!, "ana", "pass");
      const res = await postLogin(loginRequest({ username: "ana", password: "incorrecta" }));
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ error: "Usuario o contraseña incorrectos" });
    });

    it("devuelve 400 si el cuerpo no es JSON válido", async () => {
      const res = await postLogin(loginRequest("no-json"));
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({ error: "Cuerpo JSON no válido" });
    });

    it("devuelve 400 si faltan campos", async () => {
      const res = await postLogin(loginRequest({ username: "ana" }));
      expect(res.status).toBe(400);
      expect(await res.json()).toEqual({ error: "Datos no válidos" });
    });

    it("devuelve 200 y emite cookie de sesión al autenticarse", async () => {
      await seedUser(world!, "ana", "pass");
      const res = await postLogin(loginRequest({ username: "ana", password: "pass" }));
      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.ok).toBe(true);
      expect(typeof body.token).toBe("string");
      expect(body.token.length).toBeGreaterThan(0);
      const token = extractSessionToken(res);
      expect(body.token).toBe(token);
      expect(world!.data.sessions.has(token)).toBe(true);
    });
  });

  describe("POST /api/auth/logout", () => {
    it("devuelve 200 y borra la sesión activa", async () => {
      const token = seedSession(world!, "u-1", "token-salida");
      holder.authCookie.value = token;
      const res = await postLogout(new Request("http://test/api/auth/logout"));
      expect(res.status).toBe(200);
      expect(world!.data.sessions.has(token)).toBe(false);
    });

    it("devuelve 200 aunque no haya sesión", async () => {
      const res = await postLogout(new Request("http://test/api/auth/logout"));
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual({ ok: true });
    });
  });

  describe("GET /api/auth/session", () => {
    it("devuelve 401 si no hay cookie de sesión", async () => {
      const res = await getSession(new Request("http://test/api/auth/session"));
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ error: "No autenticado" });
    });

    it("devuelve el usuario con perfil calórico y rol", async () => {
      const user = await seedUser(world!, "ana", "pass", true);
      holder.authCookie.value = seedSession(world!, user.id);
      const res = await getSession(new Request("http://test/api/auth/session"));
      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.username).toBe("ana");
      expect(body.isAdmin).toBe(true);
      expect(body.calorieProfile).toEqual({
        gender: null,
        birthYear: null,
        heightCm: null,
        gymDaysPerWeek: null,
        gymSessionMinutes: null,
        walkingMinutesPerDay: null,
        calorieGoal: null,
      });
    });

    it("marca isAdmin en falso para usuarios normales", async () => {
      const user = await seedUser(world!, "ray", "pass");
      holder.authCookie.value = seedSession(world!, user.id);
      const res = await getSession(new Request("http://test/api/auth/session"));
      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.username).toBe("ray");
      expect(body.isAdmin).toBe(false);
    });

    it("devuelve el usuario autenticado con token Bearer", async () => {
      const user = await seedUser(world!, "ana", "pass");
      const token = seedSession(world!, user.id);
      const res = await getSession(
        new Request("http://test/api/auth/session", {
          headers: { authorization: `Bearer ${token}` },
        }),
      );
      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.username).toBe("ana");
    });

    it("devuelve 401 con token Bearer inválido", async () => {
      const res = await getSession(
        new Request("http://test/api/auth/session", {
          headers: { authorization: "Bearer token-invalido" },
        }),
      );
      expect(res.status).toBe(401);
    });
  });

  describe("POST /api/auth/logout con token Bearer", () => {
    it("borra la sesión indicada por el token Bearer", async () => {
      const token = seedSession(world!, "u-1", "token-bearer-salida");
      const res = await postLogout(
        new Request("http://test/api/auth/logout", {
          method: "POST",
          headers: { authorization: `Bearer ${token}` },
        }),
      );
      expect(res.status).toBe(200);
      expect(world!.data.sessions.has(token)).toBe(false);
    });
  });
});