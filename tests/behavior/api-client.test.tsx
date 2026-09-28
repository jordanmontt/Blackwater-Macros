import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, AUTH_EXPIRED_EVENT } from "@/lib/api";
import { DEMO_COOKIE_NAME } from "@/lib/demo-store";
import { jsonResponse } from "../helpers/repos";
import { applyLanguage, t } from "@/i18n";

/**
 * Requisitos del cliente de API:
 *  - una respuesta sin autenticación (401) emite el evento "app:unauthorized"
 *    (el componente AuthRedirect se encarga de navegar a /login) y lanza
 *    ApiError,
 *  - el propio endpoint de login nunca emite el evento (para poder mostrar el
 *    error en pantalla y reintentar),
 *  - un error de servidor o de red no emite el evento,
 *  - no se emite dos veces en menos de 3 segundos (evita loops).
 */

const REDIRECT_KEY = "_authRedirect";

/** Registra cada evento "app:unauthorized" disparado sobre la ventana. */
function captureAuthEvents(): string[] {
  const events: string[] = [];
  vi.spyOn(window, "dispatchEvent").mockImplementation((event) => {
    if (event && (event as CustomEvent).type === AUTH_EXPIRED_EVENT) events.push(AUTH_EXPIRED_EVENT);
    return true;
  });
  return events;
}

describe("cliente de API", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    sessionStorage.clear();
    document.cookie = `${DEMO_COOKIE_NAME}=; path=/; max-age=0`;
  });

  it("un 401 emite el evento de sesión expirada y lanza ApiError", async () => {
    const events = captureAuthEvents();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(401, { error: "No autenticado" })),
    );

    await expect(api.listMeals("2026-08-23", "2026-08-23")).rejects.toBeInstanceOf(ApiError);
    await expect(api.listMeals("2026-08-23", "2026-08-23")).rejects.toMatchObject({
      status: 401,
    });

    expect(events).toEqual([AUTH_EXPIRED_EVENT]);
    expect(sessionStorage.getItem(REDIRECT_KEY)).toBeTruthy();
  });

  it("un 401 de /api/auth/login no emite el evento: deja ver el error para reintentar", async () => {
    const events = captureAuthEvents();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(401, { error: "Usuario o contraseña incorrectos" })),
    );

    await expect(api.login("sebastian", "clave-incorrecta")).rejects.toMatchObject({
      status: 401,
    });

    expect(events).toEqual([]);
    expect(sessionStorage.getItem(REDIRECT_KEY)).toBeNull();
  });

  it("un error de servidor (500) no emite el evento", async () => {
    const events = captureAuthEvents();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(500, { error: "Error interno" })),
    );

    await expect(api.listMeals("2026-08-23", "2026-08-23")).rejects.toMatchObject({
      status: 500,
      message: "Error interno",
    });

    expect(events).toEqual([]);
    expect(sessionStorage.getItem(REDIRECT_KEY)).toBeNull();
  });

  it("el error del servidor se muestra en el idioma de la app, por su código", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(400, { error: "El peso debe estar entre 20 y 400 kg", code: "weight_out_of_range" })),
    );
    applyLanguage("de");
    try {
      await expect(api.listMeals("2026-08-23", "2026-08-23")).rejects.toMatchObject({
        status: 400,
        code: "weight_out_of_range",
        message: "Das Gewicht muss zwischen 20 und 400 kg liegen",
      });
    } finally {
      applyLanguage("es");
    }
  });

  it("un fallo de red se convierte en ApiError(0) sin emitir el evento", async () => {
    const events = captureAuthEvents();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("Failed to fetch");
      }),
    );

    await expect(api.listMeals("2026-08-23", "2026-08-23")).rejects.toMatchObject({
      status: 0,
      code: "network",
      message: t.serverErrors.network,
    });

    expect(events).toEqual([]);
  });

  it("no se emite dos veces dentro de los 3 segundos aunque vuelvan a llegar 401", async () => {
    const events = captureAuthEvents();
    sessionStorage.setItem(REDIRECT_KEY, String(Date.now() - 10_000));
    const setItemSpy = vi.spyOn(sessionStorage, "setItem");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(401, { error: "No autenticado" })),
    );

    await expect(api.listMeals("2026-08-23", "2026-08-23")).rejects.toBeInstanceOf(ApiError);
    expect(events).toEqual([AUTH_EXPIRED_EVENT]);

    // Tan solo unos milisegundos después, vuelve a llegar un 401:
    await expect(api.listMeals("2026-08-24", "2026-08-24")).rejects.toBeInstanceOf(ApiError);

    expect(events).toEqual([AUTH_EXPIRED_EVENT]);
    expect(setItemSpy).toHaveBeenCalledTimes(1);
  });
});