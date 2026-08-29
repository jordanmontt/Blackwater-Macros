/**
 * Cliente de caché stale-while-revalidate para los datos del servidor.
 *
 * Objetivo: que cambiar de pestaña y reabrir la app se sienta fluido.
 * La primera vez que se pide un recurso se muestra al instante la copia en
 * caché (memoria o sessionStorage) y en segundo plano se revalida contra la
 * base de datos para traer los datos más recientes (p. ej. los que añadió
 * otra app/dispositivo).
 *
 * Dos niveles:
 *  - memoria: sobrevive a la navegación entre páginas en una misma carga
 *    (el bundle del App Router persiste entre rutas) → resuelve el cambio
 *    de pestaña.
 *  - sessionStorage: sobrevive a recargas/reapariciones de la misma pestaña
 *    → resuelve la apertura rápida al volver a la app. Es por-pestaña, así
 *    que una pestaña nueva simplemente hace la petición normal.
 *
 * Cuando una escritura cambia los datos (crear/editar/borrar comidas, pesos,
 * plantillas, ajustes) hay que invalidar la clave para no mostrar datos
 * obsoletos: `invalidate("comidas")`.
 */

const STORAGE_KEY = "blackwater_cache_v1";

interface CacheEntry {
  ts: number;
  value: unknown;
}

type Listener = () => void;

const memory = new Map<string, CacheEntry>();
const listeners = new Set<Listener>();

let hydrated = false;

function canUseStorage(): boolean {
  try {
    return typeof window !== "undefined" && !!window.sessionStorage;
  } catch {
    return false;
  }
}

function hydrateFromStorage(): void {
  if (!canUseStorage() || hydrated) return;
  hydrated = true;
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const persisted = JSON.parse(raw) as Record<string, CacheEntry>;
    for (const [key, entry] of Object.entries(persisted)) {
      if (entry && typeof entry.value !== "undefined" && !memory.has(key)) {
        memory.set(key, entry);
      }
    }
  } catch {
    // sessionStorage corrupto o no disponible: la caché queda solo en memoria.
  }
}

function readStorage(): Record<string, CacheEntry> {
  if (!canUseStorage()) return {};
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Record<string, CacheEntry>) : {};
  } catch {
    return {};
  }
}

function writeStorage(entries: Record<string, CacheEntry>): void {
  if (!canUseStorage()) return;
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(entries));
  } catch {
    // Cuando sessionStorage está lleno o no disponible, la caché de memoria
    // sigue funcionando para la navegación entre pestañas.
  }
}

function notify(): void {
  listeners.forEach((listener) => listener());
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Devuelve el valor en caché sin revalidar. Se lee solo de memoria: los datos
 * de sessionStorage se hidratan en memoria una sola vez, de modo que la
 * referencia del valor es estable (requisito de useSyncExternalStore).
 */
export function readCache<T>(key: string): T | undefined {
  hydrateFromStorage();
  return memory.get(key)?.value as T | undefined;
}

/** Guarda un valor en memoria y sessionStorage y avisa a los suscriptores. */
export function writeCache(key: string, value: unknown): void {
  const entry: CacheEntry = { ts: Date.now(), value };
  memory.set(key, entry);
  const persisted = readStorage();
  persisted[key] = entry;
  writeStorage(persisted);
  notify();
}

/**
 * Revalida una clave contra el servidor: pide la copia en caché al instante
 * (si existe) y lanza una petición de fondo que actualiza la caché. Devuelve
 * el valor "mejor" disponible (caché o, si no hay, el recién obtenido).
 */
export async function getOrRevalidate<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  const cached = readCache<T>(key);
  if (cached !== undefined) {
    void revalidate(key, fetcher);
    return cached;
  }
  const value = await fetcher();
  writeCache(key, value);
  return value;
}

/** Petición de red siempre; actualiza la caché con el resultado. */
export async function revalidate<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  try {
    const value = await fetcher();
    writeCache(key, value);
    return value;
  } catch (error) {
    // Si la revalidación falla se conserva la copia en caché (si la hay);
    // el error se propaga para que el llamador pueda decidir.
    throw error;
  }
}

/** Elimina de la caché todas las claves que empiecen por `prefix`. */
export function invalidate(prefix: string): void {
  let changed = false;
  for (const key of [...memory.keys()]) {
    if (key.startsWith(prefix)) {
      memory.delete(key);
      changed = true;
    }
  }
  const persisted = readStorage();
  for (const key of Object.keys(persisted)) {
    if (key.startsWith(prefix)) {
      delete persisted[key];
      changed = true;
    }
  }
  if (changed) {
    writeStorage(persisted);
    notify();
  }
}

/** Vacía toda la caché (logout / salir del modo demo). */
export function clearCache(): void {
  memory.clear();
  if (canUseStorage()) {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignorar
    }
  }
  notify();
}
