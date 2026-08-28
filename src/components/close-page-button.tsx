"use client";

import { useRouter } from "next/navigation";
import { XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { t } from "@/i18n";

export function ClosePageButton() {
  const router = useRouter();
  return (
    <Button variant="ghost" size="icon" aria-label={t.common.close} onClick={() => router.back()}>
      <XIcon />
    </Button>
  );
}