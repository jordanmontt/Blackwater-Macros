import { currentLanguage } from "./index";
import { LOCALE_TAGS } from "./languages";
import { formatDecimal } from "@/lib/core/numbers";

/**
 * Dates in the active language and numbers in the app's single format (web
 * counterpart of Android `Format.kt`).
 */

const cache = new Map<string, Intl.DateTimeFormat>();

function formatter<T extends Intl.DateTimeFormat>(key: string, create: (locale: string) => T): T {
  const locale = LOCALE_TAGS[currentLanguage()];
  const id = `${locale}|${key}`;
  let found = cache.get(id) as T | undefined;
  if (!found) {
    found = create(locale);
    cache.set(id, found);
  }
  return found;
}

/**
 * The same in every language (core `formatDecimal`): «1,6», «2000», «12 345».
 * See docs/TECHNICAL.md «Numbers on screen and in inputs».
 */
export function formatNumber(value: number, maxDecimals = 0): string {
  return formatDecimal(value, maxDecimals);
}

/** «Domingo, 27 de septiembre» / «Sunday 27 September». */
export function formatDateKeyLong(key: string): string {
  const text = formatter(
    "long",
    (locale) => new Intl.DateTimeFormat(locale, { weekday: "long", day: "numeric", month: "long" }),
  ).format(new Date(`${key}T00:00:00`));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** «27 sept» / «27 Sept». */
export function formatDateKeyShort(key: string): string {
  return formatter("short", (locale) => new Intl.DateTimeFormat(locale, { day: "numeric", month: "short" })).format(
    new Date(`${key}T00:00:00`),
  );
}


/** «27 sept 2026». */
export function formatDateMedium(value: Date): string {
  return formatter("medium", (locale) => new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", year: "numeric" })).format(
    value,
  );
}
