import { currentLanguage } from "./index";
import { LOCALE_TAGS } from "./languages";

/**
 * Numbers and dates in the active language (web counterpart of Android
 * `Format.kt`). Same output as the `…Es` helpers in `core/dates.ts` in Spanish.
 */

const cache = new Map<string, Intl.NumberFormat | Intl.DateTimeFormat>();

function formatter<T extends Intl.NumberFormat | Intl.DateTimeFormat>(key: string, create: (locale: string) => T): T {
  const locale = LOCALE_TAGS[currentLanguage()];
  const id = `${locale}|${key}`;
  let found = cache.get(id) as T | undefined;
  if (!found) {
    found = create(locale);
    cache.set(id, found);
  }
  return found;
}

export function formatNumber(value: number, maxDecimals = 0): string {
  return formatter(`n${maxDecimals}`, (locale) => new Intl.NumberFormat(locale, { maximumFractionDigits: maxDecimals })).format(
    value,
  );
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
