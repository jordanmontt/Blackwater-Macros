"use client";

import { AiSettingsCard } from "@/components/settings/ai-settings-card";
import { formatTemplate, t } from "@/i18n";
import { AI_SETTINGS_ORIGINS, aiSettingsOrigin } from "@/lib/ai-settings-origin";
import { useSearchParam } from "@/lib/use-search-param";
import { SubpageHeader } from "@/components/subpage-header";

/**
 * Ajustes → Inteligencia artificial, on its own page like Perfil (and like the
 * Android screen): provider, key, model, the model in this browser and the
 * coach's access to the data. Nested under `/ajustes` so that tab stays active, unless
 * it was opened from Coach or «Foto o texto» (`?from=`, `lib/ai-settings-origin.ts`).
 */
export default function AiSettingsPage() {
  // Opened from Coach or «Foto o texto»: the arrow goes back there, not to Ajustes.
  const origin = aiSettingsOrigin(useSearchParam("from"));
  const back = origin ? AI_SETTINGS_ORIGINS[origin] : null;
  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 pb-12 md:pt-6">
      <SubpageHeader
        title={t.ai.title}
        backLabel={back ? formatTemplate(t.common.backTo, { page: back.page() }) : t.perfil.backToSettings}
        backHref={back?.href}
      />
      <AiSettingsCard embedded />
    </main>
  );
}
