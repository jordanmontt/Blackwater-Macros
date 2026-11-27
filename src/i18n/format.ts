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

/** «dom, 4 oct» / «Sun 4 Oct»: which day a new meal goes to, in a title. */
export function formatDateKeyWeekdayShort(key: string): string {
  return formatter(
    "weekdayShort",
    (locale) => new Intl.DateTimeFormat(locale, { weekday: "short", day: "numeric", month: "short" }),
  ).format(new Date(`${key}T00:00:00`));
}

/**
 * «07:40» / «7:40 AM»: the time of a moment in the language's clock, in `timeZone` (a
 * weigh-in's saved zone, so it reads at the hour it was taken there; Android `formatTime`).
 * No zone, or one the runtime doesn't know → the device's.
 */
export function formatTime(value: Date, timeZone: string | null = null): string {
  const options: Intl.DateTimeFormatOptions = { hour: "numeric", minute: "2-digit" };
  if (timeZone) {
    try {
      return formatter(`time|${timeZone}`, (locale) => new Intl.DateTimeFormat(locale, { ...options, timeZone })).format(value);
    } catch {
      // An unknown zone (RangeError): the device's, below.
    }
  }
  return formatter("time", (locale) => new Intl.DateTimeFormat(locale, options)).format(value);
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
