"use client";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { NativeSelect } from "@/components/ui/native-select";
import { t } from "@/i18n";
import { useMounted } from "@/lib/use-mounted";
import { LANGUAGE_COOKIE, LANGUAGE_NAMES, LANGUAGES, SYSTEM_LANGUAGE, isAppLanguage } from "@/i18n/languages";

function storedChoice(): string {
  if (typeof document === "undefined") return SYSTEM_LANGUAGE;
  const match = document.cookie.match(new RegExp(`(?:^|; )${LANGUAGE_COOKIE}=([^;]*)`));
  return match && isAppLanguage(match[1]) ? match[1] : SYSTEM_LANGUAGE;
}

/** «Idioma» (Android `LanguageCard`): saved in a cookie so the server renders the right language. */
export function LanguageCard() {
  // Read after hydration: the server does not know the cookie's value here.
  const mounted = useMounted();
  const choice = mounted ? storedChoice() : SYSTEM_LANGUAGE;

  function choose(value: string) {
    document.cookie = `${LANGUAGE_COOKIE}=${value}; path=/; max-age=31536000; samesite=lax`;
    // The whole app switches on the next load (see `i18n/index.ts`).
    window.location.reload();
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">
          <label htmlFor="app-language">{t.ajustes.language}</label>
        </CardTitle>
        <CardDescription>{t.ajustes.languageHint}</CardDescription>
      </CardHeader>
      <CardContent>
        <NativeSelect id="app-language" value={choice} onChange={(event) => choose(event.target.value)}>
          <option value={SYSTEM_LANGUAGE}>{t.ajustes.languageSystem}</option>
          {LANGUAGES.map((language) => (
            <option key={language} value={language}>
              {LANGUAGE_NAMES[language]}
            </option>
          ))}
        </NativeSelect>
      </CardContent>
    </Card>
  );
}
