import { t } from "@/i18n";

/**
 * Where «Configurar la IA» was opened from, when not from Ajustes: `?from=coach`
 * (Coach) or `?from=hoy` (Comidas → Foto o texto). The IA page goes back there and
 * the menu keeps that tab, instead of dropping the user in Ajustes.
 */
export const AI_SETTINGS_ORIGINS = {
  coach: { href: "/coach", page: () => t.nav.coach },
  hoy: { href: "/", page: () => t.nav.hoy },
} as const;

export type AiSettingsOrigin = keyof typeof AI_SETTINGS_ORIGINS;

export function aiSettingsOrigin(from: string | null): AiSettingsOrigin | null {
  return from !== null && from in AI_SETTINGS_ORIGINS ? (from as AiSettingsOrigin) : null;
}

/** The IA page, opened from `origin`. */
export function aiSettingsHref(origin: AiSettingsOrigin): string {
  return `/ajustes/ia?from=${origin}`;
}
