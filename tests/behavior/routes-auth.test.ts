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
import { POST as postRegister } from "@/app/api/auth/register/route";
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
      expect(await res.json()).toEqual({ ok: true });
      const token = extractSessionToken(res);
      expect(token.length).toBeGreaterThan(0);
      expect(world!.data.sessions.has(token)).toBe(true);
    });
  });

  describe("POST /api/auth/register", () => {
    it("devuelve 403 y no crea usuarios mientras el registro está deshabilitado", async () => {
      const res = await postRegister(
        loginRequest({ username: "ana", password: "clave-secreta-1" }),
      );
      expect(res.status).toBe(403);
      expect(await res.json()).toEqual({ error: "Registro deshabilitado" });
      expect(world!.data.users.size).toBe(0);
    });
  });

  describe("POST /api/auth/logout", () => {
    it("devuelve 200 y borra la sesión activa", async () => {
      const token = seedSession(world!, "u-1", "token-salida");
      holder.authCookie.value = token;
      const res = await postLogout();
      expect(res.status).toBe(200);
      expect(world!.data.sessions.has(token)).toBe(false);
    });

    it("devuelve 200 aunque no haya sesión", async () => {
      const res = await postLogout();
      expect(res.status).toBe(200);
      expect(await res.json()).toEqual({ ok: true });
    });
  });

  describe("GET /api/auth/session", () => {
    it("devuelve 401 si no hay cookie de sesión", async () => {
      const res = await getSession();
      expect(res.status).toBe(401);
      expect(await res.json()).toEqual({ error: "No autenticado" });
    });
  });
});