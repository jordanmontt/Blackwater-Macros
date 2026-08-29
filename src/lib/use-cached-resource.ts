"use client";

import { useCallback, useEffect, useRef, useSyncExternalStore } from "react";
import { getOrRevalidate, readCache, revalidate, subscribe } from "./client-cache";

/**
 * Hook para leer un recurso del servidor con la caché stale-while-revalidate
 * de `client-cache.ts`:
 *
 *  - Si hay copia en caché (memoria o sessionStorage) se muestra al instante
 *    y en segundo plano se revalida contra la base de datos.
 *  - Si no hay copia, se pide al servidor como siempre.
 *  - Cuando la clave se invalida (una escritura la elimina), se revalida sola.
 *  - Al escribir (crear/editar/borrar) se llama a `trigger()` para forzar una
 *    lectura fresca del servidor.
 *
 * `data` es `undefined` mientras no hay dato disponible (ni caché ni red).
 * Al leer de la caché devuelve la MISMA referencia mientras no cambie, así
 * que es estable (requisito de useSyncExternalStore) y reactivo (se
 * re-renderiza al cambiar o invalidarse la clave).
 */
export function useCachedResource<T>(
  key: string,
  fetcher: () => Promise<T>,
  options?: { onError?: (error: unknown) => void },
): {
  data: T | undefined;
  trigger: () => Promise<T>;
} {
  const fetcherRef = useRef<() => Promise<T>>(fetcher);
  const onErrorRef = useRef<((error: unknown) => void) | undefined>(options?.onError);

  // Mantiene la referencia al fetcher más reciente sin revalidar por cada
  // render (el fetcher es una arrow nueva en cada render).
  useEffect(() => {
    fetcherRef.current = fetcher;
  }, [fetcher]);

  useEffect(() => {
    onErrorRef.current = options?.onError;
  }, [options?.onError]);

  const data = useSyncExternalStore<T | undefined>(
    subscribe,
    () => readCache<T>(key),
    () => undefined,
  );

  useEffect(() => {
    let hadValue = readCache<T>(key) !== undefined;
    const run = () => {
      void getOrRevalidate<T>(key, () => fetcherRef.current()).catch((error) => {
        // Solo informa del fallo cuando no había copia en caché que mostrar
        // (miss inicial de red/servidor). Si había stop, la revalidación de
        // fondo que falle conserva el dato antiguo y no debe molestar.
        if (!readCache<T>(key)) onErrorRef.current?.(error);
      });
    };
    run();
    const unsubscribe = subscribe(() => {
      const nowDefined = readCache<T>(key) !== undefined;
      // Solo recarga cuando la clave pasó de tener dato a no tenerlo (una
      // invalidación por escritura). Evita recargar en pleno vuelo solo
      // porque otra clave distinta cambió.
      if (hadValue && !nowDefined) run();
      hadValue = nowDefined;
    });
    return unsubscribe;
  }, [key]);

  const trigger = useCallback(() => {
    return revalidate<T>(key, () => fetcherRef.current());
  }, [key]);

  return { data, trigger };
}
