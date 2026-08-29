import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import {
  clearCache,
  getOrRevalidate,
  invalidate,
  readCache,
  revalidate,
  subscribe,
  writeCache,
} from "@/lib/client-cache";
import { useCachedResource } from "@/lib/use-cached-resource";
import { api } from "@/lib/api";
import { jsonResponse } from "../helpers/repos";

/**
 * Requisitos de la caché cliente (stale-while-revalidate):
 *
 *  - guarda un valor y lo recupera también desde sessionStorage (sobrevive a
 *    recargas/reapariciones de la pestaña),
 *  - la primera vez que se pide un recurso se obtiene del servidor y se
 *    guarda en caché,
 *  - con una copia en caché devuelve el dato al instante y en segundo plano
 *    revalida contra el servidor para traer lo más reciente,
 *  - invalidar una clave la elimina (de memoria y storage) y avisa a los
 *    suscriptores,
 *  - vaciar la caché la deja sin datos (logout),
 *  - una escritura de la API invalida las claves relacionadas para no mostrar
 *    datos obsoletos.
 */

const STORAGE_KEY = "blackwater_cache_v1";

describe("caché cliente (stale-while-revalidate)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    clearCache();
    sessionStorage.clear();
  });

  it("guarda un valor y lo recupera, persistido también en sessionStorage", () => {
    expect(readCache<number>("clave")).toBeUndefined();
    writeCache("clave", 42);
    expect(readCache<number>("clave")).toBe(42);
    expect(sessionStorage.getItem(STORAGE_KEY)).toContain("42");
  });

  it("la primera vez pide al servidor y guarda en caché", async () => {
    const fetcher = vi.fn(async () => [1, 2, 3]);
    expect(await getOrRevalidate("lista", fetcher)).toEqual([1, 2, 3]);
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(readCache("lista")).toEqual([1, 2, 3]);
  });

  it("con copia en caché devuelve al instante y revalida en segundo plano", async () => {
    writeCache("lista2", ["viejo"]);
    const fetcher = vi.fn(async () => ["nuevo"]);

    const value = await getOrRevalidate("lista2", fetcher);
    expect(value).toEqual(["viejo"]); // devuelve al instante la copia en caché

    // La revalidación de fondo actualiza la caché con el dato más reciente.
    await vi.waitFor(() => expect(readCache("lista2")).toEqual(["nuevo"]));
  });

  it("revalidate pide siempre al servidor y actualiza la caché", async () => {
    writeCache("clave", "viejo");
    const fetcher = vi.fn(async () => "nuevo");
    expect(await revalidate("clave", fetcher)).toBe("nuevo");
    expect(readCache("clave")).toBe("nuevo");
  });

  it("invalidate elimina las claves que coinciden y avisa a los suscriptores", () => {
    writeCache("meals:2026-08-01:2026-08-01", 1);
    writeCache("templates", []);
    const listener = vi.fn();
    const unsub = subscribe(listener);

    invalidate("meals:");
    expect(readCache("meals:2026-08-01:2026-08-01")).toBeUndefined();
    expect(readCache("templates")).toEqual([]);
    expect(listener).toHaveBeenCalledTimes(1);
    unsub();
  });

  it("clearCache vacía toda la caché", () => {
    writeCache("a", 1);
    writeCache("b", 2);
    clearCache();
    expect(readCache("a")).toBeUndefined();
    expect(readCache("b")).toBeUndefined();
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("una escritura de la API invalida las claves relacionadas", async () => {
    writeCache("weights", [{ id: "w-1" }]);
    writeCache("stats:30d:2026-08-01", {});
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(200, { weight: { id: "w-1", weightKg: 80 } })),
    );

    await api.createWeight({
      measuredAt: "2026-08-01T00:00:00.000Z",
      weightKg: 80,
    });

    expect(readCache("weights")).toBeUndefined();
    expect(readCache("stats:30d:2026-08-01")).toBeUndefined();
  });

  it("crear una comida invalida la lista del día y las estadísticas", async () => {
    writeCache("meals:2026-08-01:2026-08-01", []);
    writeCache("stats:7d:2026-08-01", {});
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(201, { meal: { id: "m-1" } })),
    );

    await api.createMeal({
      logDate: "2026-08-01",
      title: "Desayuno",
      entryMode: "total_only",
      ingredients: [],
      totalCalories: 300,
      totalProtein: 20,
    });

    expect(readCache("meals:2026-08-01:2026-08-01")).toBeUndefined();
    expect(readCache("stats:7d:2026-08-01")).toBeUndefined();
  });

  // Regresión: reordenar no debe invalidar "meals:". La página ya escribió el
  // orden optimista en la caché; invalidar aquí provocaba un parpadeo de
  // carga tras cada arrastre.
  it("reordenar comidas NO invalida la lista (sin parpadeo tras el arrastre)", async () => {
    writeCache("meals:2026-08-01:2026-08-01", [{ id: "m-1" }, { id: "m-2" }]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(200, { ok: true })),
    );

    await api.reorderMeals(["m-2", "m-1"]);

    expect(readCache("meals:2026-08-01:2026-08-01")).toEqual([{ id: "m-1" }, { id: "m-2" }]);
  });
});

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => (resolve = r));
  return { promise, resolve };
}

function Probe({
  fetcherA,
  fetcherB,
}: {
  fetcherA: () => Promise<string[]>;
  fetcherB: () => Promise<string[]>;
}) {
  const a = useCachedResource<string[]>("probe-a", fetcherA);
  useCachedResource<string[]>("probe-b", fetcherB);
  return (
    <div>
      <span data-testid="probe-a">{a.data ? a.data.join(",") : "pending"}</span>
    </div>
  );
}

// Regresión: escribir o invalidar una clave NO debe provocar que una clave
// distinta que todavía está en vuelo (primera carga sin caché) se vuelva a
// pedir. Antes, un cambio en cualquier clave recargaba en pleno vuelo todas
// las claves aún sin dato (p. ej. "tpl|wt|wt").
describe("no se recarga en pleno vuelo por claves ajenas", () => {
  it("una escritura en otra clave no revalida una clave pendiente", async () => {
    const d = deferred<string[]>();
    const fetcherA = vi.fn(() => d.promise);
    const fetcherB = vi.fn(async () => ["b"]);

    render(<Probe fetcherA={fetcherA} fetcherB={fetcherB} />);

    expect(fetcherA).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("probe-a").textContent).toBe("pending");

    // Cambia claves distintas y espera a que se propague la notificación.
    writeCache("probe-b", ["b"]);
    invalidate("probe-b");
    await new Promise((r) => setTimeout(r, 30));

    // La clave A sigue en vuelo y su fetcher no debe haberse vuelto a llamar.
    expect(fetcherA).toHaveBeenCalledTimes(1);

    d.resolve(["a"]);
    await waitFor(() => expect(screen.getByTestId("probe-a").textContent).toBe("a"));
    expect(fetcherA).toHaveBeenCalledTimes(1);
  });

  it("una invalidación de la propia clave sí recarga", async () => {
    const d = deferred<string[]>();
    const fetcherA = vi.fn(() => d.promise);

    render(<Probe fetcherA={fetcherA} fetcherB={() => Promise.resolve([])} />);
    expect(fetcherA).toHaveBeenCalledTimes(1);

    d.resolve(["a"]);
    await waitFor(() => expect(screen.getByTestId("probe-a").textContent).toBe("a"));
    expect(fetcherA).toHaveBeenCalledTimes(1);

    // Al invalidar "probe-a" (había dato y ya no), el hook vuelve a cargar.
    invalidate("probe-a");
    await waitFor(() => expect(fetcherA).toHaveBeenCalledTimes(2));
  });
});
