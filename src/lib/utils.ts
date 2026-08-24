import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Normalizes a numeric string so it always uses `,` as decimal separator. */
export function normalizeDecimal(value: string): string {
  return value.replace(/\./g, ",")
}

/** Formats a number for editing in a text input, using `,` as decimal separator. */
export function toDecimalInput(value: number): string {
  return String(value).replace(".", ",")
}
