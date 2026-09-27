"use client";

import { useMounted } from "@/lib/use-mounted";

/**
 * The server renders the app in Spanish only. For another language it sends
 * the page without this content and the browser renders it once the right
 * dictionary is loaded (see `i18n/index.ts`), so server and browser never
 * disagree about the text.
 */
export function LanguageGate({ serverRendered, children }: { serverRendered: boolean; children: React.ReactNode }) {
  const mounted = useMounted();
  if (serverRendered || mounted) return <>{children}</>;
  return null;
}
