/**
 * A JSON value kept in this browser's localStorage, as an external store for
 * `useSyncExternalStore`: `get` returns the same object until the value changes
 * (also from another tab), `set` stores it and tells the subscribers.
 * `storage` picks another place on each read and write (`userDataStorage`: the
 * tab's sessionStorage in the demo).
 */
export function localJsonStore<T>(
  key: string,
  parse: (raw: string | null) => T,
  storage: () => Storage = () => localStorage,
) {
  const listeners = new Set<() => void>();
  let cachedRaw: string | null | undefined;
  let cached: T;

  function readRaw(): string | null {
    try {
      return storage().getItem(key);
    } catch {
      return null;
    }
  }

  function get(): T {
    const raw = readRaw();
    if (raw !== cachedRaw) {
      cachedRaw = raw;
      cached = parse(raw);
    }
    return cached;
  }

  function set(next: T): void {
    try {
      storage().setItem(key, JSON.stringify(next));
    } catch {
      // Private mode / storage full: the value only lasts for this page.
      cachedRaw = JSON.stringify(next);
      cached = next;
    }
    listeners.forEach((listener) => listener());
  }

  function subscribe(listener: () => void): () => void {
    listeners.add(listener);
    const onStorage = (event: StorageEvent) => {
      if (event.key === key) listener();
    };
    window.addEventListener("storage", onStorage);
    return () => {
      listeners.delete(listener);
      window.removeEventListener("storage", onStorage);
    };
  }

  return { get, set, subscribe };
}
