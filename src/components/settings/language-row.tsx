"use client";

import { LanguagesIcon } from "lucide-react";
import { SettingsRow } from "@/components/settings/settings-list";
import { NativeSelect } from "@/components/ui/native-select";
import { t } from "@/i18n";
import { useMounted } from "@/lib/use-mounted";
import { LANGUAGE_COOKIE, LANGUAGE_NAMES, LANGUAGES, SYSTEM_LANGUAGE, isAppLanguage } from "@/i18n/languages";

function storedChoice(): string {
  if (typeof document === "undefined") return SYSTEM_LANGUAGE;
  const match = document.cookie.match(new RegExp(`(?:^|; )${LANGUAGE_COOKIE}=([^;]*)`));
  return match && isAppLanguage(match[1]) ? match[1] : SYSTEM_LANGUAGE;
}

/** «Idioma» (Android `LanguageRow`): saved in a cookie so the server renders the right language. */
export function LanguageRow() {
  // Read after hydration: the server does not know the cookie's value here.
  const mounted = useMounted();
  const choice = mounted ? storedChoice() : SYSTEM_LANGUAGE;

  function choose(value: string) {
    document.cookie = `${LANGUAGE_COOKIE}=${value}; path=/; max-age=31536000; samesite=lax`;
    // The whole app switches on the next load (see `i18n/index.ts`).
    window.location.reload();
  }

  return (
    <SettingsRow
      icon={LanguagesIcon}
      title={<label htmlFor="app-language">{t.ajustes.language}</label>}
      description={t.ajustes.languageHint}
      below={
        <NativeSelect id="app-language" className="max-w-56" value={choice} onChange={(event) => choose(event.target.value)}>
          <option value={SYSTEM_LANGUAGE}>{t.ajustes.languageSystem}</option>
          {LANGUAGES.map((language) => (
            <option key={language} value={language}>
              {LANGUAGE_NAMES[language]}
            </option>
          ))}
        </NativeSelect>
      }
    />
  );
}
