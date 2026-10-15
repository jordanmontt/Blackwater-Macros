import Link from "next/link";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";

/** The title of a page opened from Ajustes, with the arrow back to it. */
export function SubpageHeader({ title, backLabel }: { title: string; backLabel: string }) {
  return (
    <header className="flex items-center justify-between gap-2">
      <h1 className="text-lg font-semibold">{title}</h1>
      <Button
        variant="ghost"
        size="icon-sm"
        nativeButton={false}
        render={<Link href="/ajustes" aria-label={backLabel} />}
      >
        <ArrowLeftIcon />
      </Button>
    </header>
  );
}
