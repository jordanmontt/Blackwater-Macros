const DATE_KEY_RE = /^\d{4}-\d{2}-\d{2}$/;

export function isValidDateKey(value: string): boolean {
  if (!DATE_KEY_RE.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00`);
  return !Number.isNaN(parsed.getTime()) && toDateKey(parsed) === value;
}

export function toDateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function todayKey(): string {
  return toDateKey(new Date());
}

/**
 * The device's time zone («Europe/Zurich»), saved with each new weigh-in: a
 * move to another zone usually means another scale, so the weight trend compares
 * the weigh-ins before and after it separately. Null if the runtime can't say.
 */
export function deviceTimeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

export function addDaysToKey(key: string, days: number): string {
  const date = new Date(`${key}T00:00:00`);
  date.setDate(date.getDate() + days);
  return toDateKey(date);
}

/** The Monday of `key`'s week (weeks start on Monday, as in ISO 8601). */
export function weekStartOf(key: string): string {
  const weekday = new Date(`${key}T00:00:00`).getDay(); // 0 = Sunday
  return addDaysToKey(key, -(weekday === 0 ? 6 : weekday - 1));
}

export function daysBetweenKeys(from: string, to: string): number {
  const a = new Date(`${from}T00:00:00`).getTime();
  const b = new Date(`${to}T00:00:00`).getTime();
  return Math.round((b - a) / 86_400_000);
}

/**
 * Parses a `datetime-local` input value ("YYYY-MM-DDTHH:mm") as local time.
 */
export function parseLocalDateTime(value: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** Value for a `datetime-local` input from a Date, in local time. */
export function toDateTimeLocalValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

export function nowDateTimeLocalValue(): string {
  return toDateTimeLocalValue(new Date());
}

const zonedParts = new Map<string, Intl.DateTimeFormat>();

/** The formatter that reads a moment's wall clock in `timeZone`, or null if the runtime doesn't know the zone. */
function wallClockFormatter(timeZone: string): Intl.DateTimeFormat | null {
  let found = zonedParts.get(timeZone);
  if (!found) {
    try {
      found = new Intl.DateTimeFormat("en-US", {
        timeZone,
        hourCycle: "h23",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      });
    } catch {
      return null;
    }
    zonedParts.set(timeZone, found);
  }
  return found;
}

/** `timeMs`'s wall clock in the zone, read as if it were UTC: the zone's offset is that minus `timeMs`. */
function wallClockAsUtc(format: Intl.DateTimeFormat, timeMs: number): number {
  const part: Record<string, number> = {};
  for (const { type, value } of format.formatToParts(new Date(timeMs))) part[type] = Number(value);
  return Date.UTC(part.year, part.month - 1, part.day, part.hour, part.minute, part.second);
}

/**
 * The wall clock («YYYY-MM-DDTHH:mm») of a weigh-in where it was taken: `instant` in its
 * saved `timeZone`, so a weigh-in made on waking in France still reads 08:00 after a move
 * to Bolivia. No zone (older weigh-ins), or one the runtime doesn't know → the device's.
 */
export function toZonedDateTimeValue(instant: Date, timeZone: string | null): string {
  const format = timeZone ? wallClockFormatter(timeZone) : null;
  if (!format) return toDateTimeLocalValue(instant);
  return new Date(wallClockAsUtc(format, instant.getTime())).toISOString().slice(0, 16);
}

/**
 * The calendar day of a weigh-in where it was taken (its saved zone, else the device's):
 * the day it is listed under and the one the statistics, the measured expenditure and the
 * coach count it on, whichever clock computes them (the server's is UTC).
 */
export function weighInDay(instant: Date, timeZone: string | null | undefined): string {
  return toZonedDateTimeValue(instant, timeZone ?? null).slice(0, 10);
}

/**
 * The moment a wall clock («YYYY-MM-DDTHH:mm») in `timeZone` stands for: what the weigh-in
 * form saves when it shows the weigh-in's own zone. No zone, or an unknown one → the
 * device's (`parseLocalDateTime`). As `java.time`'s `atZone` (Android): an hour that
 * happens twice (summer time ends) is the first one; one that doesn't exist (it starts)
 * moves later by the gap.
 */
export function parseZonedDateTime(value: string, timeZone: string | null): Date | null {
  const format = timeZone ? wallClockFormatter(timeZone) : null;
  if (!format) return parseLocalDateTime(value);
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!match) return null;
  const [year, month, day, hour, minute] = match.slice(1).map(Number);
  const wall = Date.UTC(year, month - 1, day, hour, minute);
  if (Number.isNaN(wall) || new Date(wall).toISOString().slice(0, 16) !== value.slice(0, 16)) return null;
  // The offsets a day before and after (at most one change in between): the first one
  // that gives back this wall clock wins; none does in a gap, where the earlier one applies.
  const offsetAt = (timeMs: number) => wallClockAsUtc(format, timeMs) - timeMs;
  const before = offsetAt(wall - 86_400_000);
  const after = offsetAt(wall + 86_400_000);
  for (const offset of [before, after]) {
    if (offsetAt(wall - offset) === offset) return new Date(wall - offset);
  }
  return new Date(wall - before);
}
