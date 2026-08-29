import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "@/proxy";

/**
 * Requisitos del guard de rutas (proxy):
 *  - /login solo redirige a inicio cuando existe una sesión real; la cookie de
 *    demo la deja accesible para poder salir del modo demo,
 *  - el resto de páginas requieren sesión o cookie de demo; sin ellas se
 *    redirige a /login,
 *  - la cookie de demo permite renderizar la página pero no concede acceso a
 *    la API (eso lo garantizan las rutas HTTP, no este guard).
 */

function buildRequest(path: string, cookies: Record<string, string> = {}): NextRequest {
  const cookieHeader = Object.entries(cookies)
    .map(([name, value]) => `${name}=${value}`)
    .join("; ");
  return new NextRequest(`http://localhost:3000${path}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : {},
  });
}

describe("proxy (guard de rutas)", () => {
  it("deja pasar /login sin cookies", () => {
    const res = proxy(buildRequest("/login"));
    expect(res.status).toBe(200);
  });

  it("redirige /login al inicio si hay sesión activa", () => {
    const res = proxy(buildRequest("/login", { bw_session: "token-abc" }));
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe("http://localhost:3000/");
  });

  it("mantiene /login accesible con la cookie de demo para poder salir del modo demo", () => {
    const res = proxy(buildRequest("/login", { bw_demo: "1" }));
    expect(res.status).toBe(200);
  });

  it("redirige el resto de páginas a /login sin cookies", () => {
    for (const path of ["/", "/peso", "/estadisticas", "/ajustes", "/admin", "/metodologia"]) {
      const res = proxy(buildRequest(path));
      expect(res.status, path).toBe(307);
      expect(res.headers.get("location"), path).toBe("http://localhost:3000/login");
    }
  });

  it("deja pasar a la aplicación con la cookie de sesión", () => {
    const res = proxy(buildRequest("/", { bw_session: "token-abc" }));
    expect(res.status).toBe(200);
  });

  it("deja pasar a la aplicación con la cookie de demo (renderizar modo demo)", () => {
    const res = proxy(buildRequest("/peso", { bw_demo: "1" }));
    expect(res.status).toBe(200);
  });
});