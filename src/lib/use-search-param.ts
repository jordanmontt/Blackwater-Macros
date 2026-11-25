"use client";

import { useSyncExternalStore } from "react";

const noSubscribe = () => () => {};

/**
 * A query parameter of the current URL, read on the client. Next's
 * `useSearchParams` needs a Suspense boundary around a statically rendered page;
 * this does not, and it is read again whenever the component renders (a
 * navigation re-renders the pages and the menu). Null on the server.
 */
export function useSearchParam(name: string): string | null {
  return useSyncExternalStore(
    noSubscribe,
    () => new URLSearchParams(window.location.search).get(name),
    () => null,
  );
}
