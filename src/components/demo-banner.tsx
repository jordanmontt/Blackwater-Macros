"use client";

import { useRouter } from "next/navigation";
import { SparklesIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { exitDemoMode } from "@/lib/demo-store";
import { useDemoMode } from "@/lib/use-demo-mode";
import { t } from "@/i18n";

/**
 * Shown across all app pages while demo mode is active: a persistent banner
 * reminding the user their changes are local, with a clear way to exit back
 * to the login screen.
 */
export function DemoBanner() {
  const router = useRouter();
  const active = useDemoMode();

  if (!active) return null;

  function handleExit() {
    exitDemoMode();
    router.replace("/login");
    router.refresh();
  }

  return (
    <div className="border-b bg-primary/5 px-4 py-2">
      <div className="mx-auto flex w-full max-w-2xl items-center justify-between gap-3">
        <p className="flex items-center gap-2 text-xs text-muted-foreground">
          <SparklesIcon className="size-4 shrink-0 text-primary" />
          <span>{t.demo.banner}</span>
        </p>
        <Button variant="outline" size="sm" onClick={handleExit} className="shrink-0">
          {t.demo.exit}
        </Button>
      </div>
    </div>
  );
}
