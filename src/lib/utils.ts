import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"
import { formatDecimal } from "./core/numbers"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Normalizes a numeric string so it always uses `,` as decimal separator. */
export { normalizeDecimal } from "./core/numbers"

/** Formats a number for editing in a text input: `,` as decimal separator, no grouping. */
export function toDecimalInput(value: number): string {
  return formatDecimal(value, 6, false)
}
