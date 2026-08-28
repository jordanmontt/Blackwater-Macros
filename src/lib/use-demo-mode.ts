"use client";

import { useSyncExternalStore } from "react";
import { isDemoMode } from "./demo-store";

const emptySubscribe = () => () => {};

function getSnapshot(): boolean {
  return isDemoMode();
}

/**
 * Tracks demo mode reactively without setState-in-effect. Returns the demo
 * cookie state, where the server snapshot (`false`) differs from the client
 * snapshot so there is no hydration mismatch warning.
 */
export function useDemoMode(): boolean {
  return useSyncExternalStore(
    emptySubscribe,
    getSnapshot, // client snapshot
    () => false, // server snapshot
  );
}
