"use client";

import Link from "next/link";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AiSettingsCard } from "@/components/settings/ai-settings-card";
import { t } from "@/i18n";

/**
 * Ajustes → Inteligencia artificial, on its own page like Perfil (and like the
 * Android screen): provider, key, model, the model in this browser and the
 * coach's access to the data. Nested under `/ajustes` so that tab stays active.
 */
export default function AiSettingsPage() {
  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 pb-12 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.ai.title}</h1>
        <Button
          variant="ghost"
          size="icon-sm"
          nativeButton={false}
          render={<Link href="/ajustes" aria-label={t.perfil.backToSettings} />}
        >
          <ArrowLeftIcon />
        </Button>
      </header>
      <AiSettingsCard embedded />
    </main>
  );
}
