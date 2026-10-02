/**
 * How every number is written on screen and in inputs, the same in every
 * language and on web and Android (docs/TECHNICAL.md «Numbers on screen and in
 * inputs»): `,` for decimals, a narrow no-break space between thousands from
 * five digits up («1,6», «2000», «12 345»). The wire format, CSV and AI prompts
 * keep `.`.
 */

/** Narrow no-break space (SI / ISO 80000 thousands separator). */
export const THOUSANDS_SEPARATOR = "\u202F";

/** «1,6» / «12 345,5»: at most [maxDecimals] decimals, trailing zeros dropped. */
export function formatDecimal(value: number, maxDecimals = 0, grouping = true): string {
  const factor = 10 ** maxDecimals;
  const rounded = Math.round(Math.abs(value) * factor) / factor;
  const [integer, fraction = ""] = rounded.toFixed(maxDecimals).split(".");
  const decimals = fraction.replace(/0+$/, "");
  const digits = grouping && integer.length >= 5 ? groupThousands(integer) : integer;
  const sign = value < 0 && rounded !== 0 ? "-" : "";
  return sign + digits + (decimals ? `,${decimals}` : "");
}

function groupThousands(integer: string): string {
  let out = "";
  for (let i = 0; i < integer.length; i++) {
    if (i > 0 && (integer.length - i) % 3 === 0) out += THOUSANDS_SEPARATOR;
    out += integer[i];
  }
  return out;
}

/** What the user types in a number field: a `.` becomes `,` at once. */
export function normalizeDecimal(value: string): string {
  return value.replace(/\./g, ",");
}
