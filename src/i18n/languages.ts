/**
 * Web languages (same five as the Android app). The server picks one per
 * request from the `bw-lang` cookie, or the browser's Accept-Language when the
 * user chose «Sistema», and writes it into `<html lang>`; the browser reads it
 * back from there, so server and browser always agree.
 */
export const LANGUAGES = ["es", "en", "fr", "it", "de"] as const;
export type AppLanguage = (typeof LANGUAGES)[number];

export const DEFAULT_LANGUAGE: AppLanguage = "es";
export const LANGUAGE_COOKIE = "bw-lang";
/** Cookie value for «follow the browser». */
export const SYSTEM_LANGUAGE = "system";

/** Each language in its own words, for the selector. */
export const LANGUAGE_NAMES: Record<AppLanguage, string> = {
  es: "Español",
  en: "English",
  fr: "Français",
  it: "Italiano",
  de: "Deutsch",
};

/** Number and date formats (metric, day before month). */
export const LOCALE_TAGS: Record<AppLanguage, string> = {
  es: "es-ES",
  en: "en-GB",
  fr: "fr-FR",
  it: "it-IT",
  de: "de-DE",
};

/** The AI is told in English which language to answer in (Android `aiLanguageName`). */
export const AI_LANGUAGE_NAMES: Record<AppLanguage, string> = {
  es: "Spanish",
  en: "English",
  fr: "French",
  it: "Italian",
  de: "German",
};

export function isAppLanguage(value: string | null | undefined): value is AppLanguage {
  return (LANGUAGES as readonly string[]).includes(value ?? "");
}

/** The first supported language in an Accept-Language header («fr-CH,fr;q=0.9,en;q=0.8»). */
export function languageFromAcceptLanguage(header: string | null | undefined): AppLanguage | null {
  if (!header) return null;
  const ranked = header
    .split(",")
    .map((part) => {
      const [tag, ...params] = part.trim().split(";");
      const q = params.find((param) => param.trim().startsWith("q="));
      return { code: tag.trim().slice(0, 2).toLowerCase(), q: q ? Number(q.trim().slice(2)) : 1 };
    })
    .filter((entry) => entry.code && !Number.isNaN(entry.q))
    .sort((a, b) => b.q - a.q);
  return ranked.map((entry) => entry.code).find(isAppLanguage) ?? null;
}

/** Cookie first (an explicit choice), then the browser's languages, else Spanish. */
export function resolveLanguage(cookieValue: string | null | undefined, acceptLanguage: string | null | undefined): AppLanguage {
  if (isAppLanguage(cookieValue)) return cookieValue;
  return languageFromAcceptLanguage(acceptLanguage) ?? DEFAULT_LANGUAGE;
}
