"use client";

import { useRouter } from "next/navigation";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { t } from "@/i18n";

/**
 * ← at the start of a page opened from many places (Metodología): back to wherever the
 * user came from, like the ← of Perfil and IA (it used to be an ✕ at the end).
 */
export function BackPageButton() {
  const router = useRouter();
  return (
    <Button variant="ghost" size="icon" className="-ml-2 size-11" aria-label={t.addFood.back} onClick={() => router.back()}>
      <ArrowLeftIcon />
    </Button>
  );
}
