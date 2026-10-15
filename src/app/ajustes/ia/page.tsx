"use client";

import { AiSettingsCard } from "@/components/settings/ai-settings-card";
import { t } from "@/i18n";
import { SubpageHeader } from "@/components/subpage-header";

/**
 * Ajustes → Inteligencia artificial, on its own page like Perfil (and like the
 * Android screen): provider, key, model, the model in this browser and the
 * coach's access to the data. Nested under `/ajustes` so that tab stays active.
 */
export default function AiSettingsPage() {
  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 pb-12 md:pt-6">
      <SubpageHeader title={t.ai.title} backLabel={t.perfil.backToSettings} />
      <AiSettingsCard embedded />
    </main>
  );
}
