import { t } from "./es";

export { t };

// Para añadir otro idioma en el futuro: crea `en.ts` con la misma estructura
// y exporta aquí el diccionario activo (p. ej. según una preferencia guardada).
export type Dictionary = typeof t;

/** Sustituye "{n}" y similares por valores. */
export function formatTemplate(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) =>
    key in values ? String(values[key]) : `{${key}}`,
  );
}
