import { describe, expect, it } from "vitest";
import { Hono } from "hono";
import { z } from "zod";
import type { AuthServiceDeps } from "@/server/services/auth-service";
import { sessionTokenFromRequest, withUserId } from "../src/helpers";

async function probe(headers: Record<string, string>): Promise<{ token: string | null }> {
  const app = new Hono();
  app.get("/probe", (c) => c.json({ token: sessionTokenFromRequest(c) ?? null }));
  const res = await app.request("/probe", { headers });
  return (await res.json()) as { token: string | null };
}

describe("sessionTokenFromRequest", () => {
  it("lee el token Bearer del header Authorization", async () => {
    await expect(probe({ Authorization: "Bearer abc123" })).resolves.toEqual({
      token: "abc123",
    });
  });

  it("ignora headers Authorization que no sean Bearer", async () => {
    await expect(probe({ Authorization: "Basic abc123" })).resolves.toEqual({ token: null });
  });

  it("lee la sesión de la cookie bw_session", async () => {
    await expect(probe({ Cookie: "otra=1; bw_session=token-x; foo=2" })).resolves.toEqual({
      token: "token-x",
    });
  });

  it("devuelve undefined si no se presenta nada", async () => {
    await expect(probe({})).resolves.toEqual({ token: null });
  });
});

function makeAuthDeps(): AuthServiceDeps {
  return {
    users: {
      findByUsername: async () => null,
      create: async () => ({}) as never,
    },
    sessions: {
      create: async () => undefined,
      findByToken: async (token: string) =>
        token === "valido"
          ? { token, userId: "u1", expiresAt: new Date(Date.now() + 100_000) }
          : null,
      deleteByToken: async () => undefined,
      deleteExpiredBefore: async () => undefined,
    },
  };
}

function appWithDeps(deps: AuthServiceDeps): Hono {
  const app = new Hono();
  app.get("/privado", withUserId(deps, async (_c, userId) => new Response(userId)));
  app.get(
    "/zod",
    withUserId(deps, async () => {
      z.string().min(1, "El título es obligatorio").parse("");
      return new Response("nunca");
    }),
  );
  app.get(
    "/boom",
    withUserId(deps, async () => {
      throw new Error("base de datos caída");
    }),
  );
  return app;
}

describe("withUserId", () => {
  const app = appWithDeps(makeAuthDeps());

  it("rechaza con 401 si el token es válido", async () => {
    const res = await app.request("/privado", { headers: { Authorization: "Bearer valido" } });
    expect(res.status).toBe(200);
    await expect(res.text()).resolves.toBe("u1");
  });

  it("rechaza con 401 si no hay token", async () => {
    const res = await app.request("/privado");
    expect(res.status).toBe(401);
    await expect(res.json()).resolves.toEqual({ error: "No autenticado" });
  });

  it("rechaza con 401 si el token es desconocido (incluida la cookie)", async () => {
    const res = await app.request("/privado", {
      headers: { Cookie: "bw_session=inexistente" },
    });
    expect(res.status).toBe(401);
  });

  it("devuelve 400 con el mensaje de validación cuando el body no es válido", async () => {
    const res = await app.request("/zod", { headers: { Authorization: "Bearer valido" } });
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error).toBe("El título es obligatorio");
  });

  it("devuelve 500 genérico ante un error inesperado", async () => {
    const res = await app.request("/boom", { headers: { Authorization: "Bearer valido" } });
    expect(res.status).toBe(500);
    await expect(res.json()).resolves.toEqual({ error: "Error interno" });
  });
});