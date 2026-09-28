import { t as es } from "./es";
import { en } from "./en";
import { fr } from "./fr";
import { it } from "./it";
import { de } from "./de";
import { DEFAULT_LANGUAGE, isAppLanguage, type AppLanguage } from "./languages";

/** The dictionary shape with plain strings (es.ts is `as const`). */
type Widen<T> = T extends string
  ? string
  : T extends readonly (infer U)[]
    ? readonly Widen<U>[]
    : { readonly [K in keyof T]: Widen<T[K]> };

export type Dictionary = Widen<typeof es>;

const DICTIONARIES: Record<AppLanguage, Dictionary> = { es, en, fr, it, de };

/**
 * The active dictionary. Components read `t.section.key` while rendering; the
 * browser switches it once, when this module loads, to the language the server
 * wrote into `<html lang>` (see `languages.ts`). The server always renders
 * Spanish and leaves other languages to the browser (`LanguageGate`).
 */
export const t: Dictionary = { ...es };

let current: AppLanguage = DEFAULT_LANGUAGE;

/** A language's dictionary without switching the shared `t` (server-side titles). */
export function dictionaryFor(language: AppLanguage): Dictionary {
  return DICTIONARIES[language];
}

export function currentLanguage(): AppLanguage {
  return current;
}

export function applyLanguage(language: AppLanguage): void {
  if (language === current) return;
  Object.assign(t, DICTIONARIES[language]);
  current = language;
}

if (typeof document !== "undefined") {
  const language = document.documentElement.lang;
  if (isAppLanguage(language)) applyLanguage(language);
}

/** Sustituye "{n}" y similares por valores. */
export function formatTemplate(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) =>
    key in values ? String(values[key]) : `{${key}}`,
  );
}
