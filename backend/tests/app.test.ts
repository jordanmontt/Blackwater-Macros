import { describe, expect, it } from "vitest";
import type { Hono } from "hono";
import { createApp } from "../src/app";
import { makeMemoryDeps, seedUser } from "./fakes";

const VALID_MEAL = {
  logDate: "2026-08-28",
  title: "Desayuno",
  notes: null,
  entryMode: "total_only",
  ingredients: [],
  totalCalories: 400,
  totalProtein: 20,
};

async function loginToken(
  app: Hono,
  username: string,
  password: string,
): Promise<{ token: string; expiresAt: string }> {
  const res = await app.request("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  expect(res.status).toBe(200);
  const body = (await res.json()) as { ok: boolean; token: string; expiresAt: string };
  expect(body.ok).toBe(true);
  return { token: body.token, expiresAt: body.expiresAt };
}

async function authed(): Promise<{ app: Hono; token: string }> {
  const deps = makeMemoryDeps();
  const app = createApp(deps);
  const credentials = await seedUser(deps);
  const { token } = await loginToken(app, credentials.username, credentials.password);
  return { app, token };
}

async function postMeal(app: Hono, token: string, meal: unknown = VALID_MEAL) {
  return app.request("/api/meals", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(meal),
  });
}

describe("login y sesión", () => {
  it("devuelve el token en el cuerpo y fija la cookie bw_session", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);
    const credentials = await seedUser(deps);

    const res = await app.request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: credentials.username, password: credentials.password }),
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { token: string; expiresAt: string };
    expect(body.token).toBeTruthy();
    expect(body.expiresAt).toBeTruthy();
    expect(res.headers.get("set-cookie")).toContain("bw_session=");
  });

  it("rechaza con 401 contraseñas erróneas", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);
    await seedUser(deps);

    const res = await app.request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "sebastian", password: "incorrecta" }),
    });
    expect(res.status).toBe(401);
    expect((await res.json()) as { error: string }).toEqual({ error: "INVALID_PASSWORD" });
  });

  it("distingue un usuario inexistente (USER_NOT_FOUND)", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);

    const res = await app.request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "nadie", password: "loquesea" }),
    });
    expect(res.status).toBe(401);
    expect((await res.json()) as { error: string }).toEqual({ error: "USER_NOT_FOUND" });
  });

  it("no filtra si el usuario existe pero con buena contraseña", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);
    const credentials = await seedUser(deps);

    const wrong = await app.request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: credentials.username, password: "incorrecta" }),
    });
    expect(wrong.status).toBe(401);
    expect((await wrong.json()) as { error: string }).toEqual({ error: "INVALID_PASSWORD" });
  });

  it("expone usuario y perfil en /api/auth/session", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);
    const credentials = await seedUser(deps);
    const { token } = await loginToken(app, credentials.username, credentials.password);

    const res = await app.request("/api/auth/session", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as { username: string; calorieProfile: object };
    expect(body.username).toBe(credentials.username);
    expect(body.calorieProfile).toHaveProperty("calorieGoal");
  });

  it("sin token ninguna ruta protegida responde", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);
    const res = await app.request("/api/meals");
    expect(res.status).toBe(401);
  });

  it("el cierre de sesión invalida el token de inmediato", async () => {
    const deps = makeMemoryDeps();
    const app = createApp(deps);
    const credentials = await seedUser(deps);
    const { token } = await loginToken(app, credentials.username, credentials.password);

    const logoutRes = await app.request("/api/auth/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(logoutRes.status).toBe(200);

    const res = await app.request("/api/meals", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status).toBe(401);
  });
});

describe("contratos de las rutas", () => {
  it("crear una comida y listarla por rango", async () => {
    const { app, token } = await authed();

    const created = await postMeal(app, token);
    expect(created.status).toBe(201);
    const createdBody = (await created.json()) as { meal: { id: string; title: string } };
    expect(createdBody.meal.title).toBe("Desayuno");

    const listed = await app.request("/api/meals?from=2026-08-01&to=2026-08-31", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(listed.status).toBe(200);
    const listedBody = (await listed.json()) as { meals: unknown[] };
    expect(listedBody.meals).toHaveLength(1);
  });

  it("valida el payload y devuelve 400 con mensaje en español", async () => {
    const { app, token } = await authed();
    const res = await postMeal(app, token, {
      logDate: "mal",
      entryMode: "total_only",
      ingredients: [],
    });
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: string };
    expect(body.error.length).toBeGreaterThan(0);
  });

  it("404 al editar una comida inexistente", async () => {
    const { app, token } = await authed();
    const res = await app.request("/api/meals/00000000-0000-4000-8000-000000000099", {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(VALID_MEAL),
    });
    expect(res.status).toBe(404);
  });

  it("exporta comidas como CSV con BOM y cabecera", async () => {
    const { app, token } = await authed();
    await postMeal(app, token);

    const res = await app.request("/api/export/meals.csv", {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toContain("text/csv");
    const bytes = new Uint8Array(await res.arrayBuffer());
    expect([bytes[0], bytes[1], bytes[2]]).toEqual([0xef, 0xbb, 0xbf]); // BOM UTF-8
    expect(new TextDecoder().decode(bytes)).toContain("fecha");
  });
});

describe("CORS", () => {
  it("permite orígenes configurados", async () => {
    const { app } = await authed();
    const res = await app.request("/api/meals", {
      headers: { Origin: "http://localhost:8080", Authorization: "Bearer bogus" },
    });
    expect(res.headers.get("access-control-allow-origin")).toBe("http://localhost:8080");
  });

  it("responde a las peticiones preflight OPTIONS con los headers permitidos", async () => {
    const { app } = await authed();
    const res = await app.request("/api/meals", {
      method: "OPTIONS",
      headers: {
        Origin: "http://localhost:8080",
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "authorization, content-type",
      },
    });
    expect(res.status).toBe(204);
    expect(res.headers.get("access-control-allow-origin")).toBe("http://localhost:8080");
    expect(res.headers.get("access-control-allow-headers")).toContain("Authorization");
  });
});