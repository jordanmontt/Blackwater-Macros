import Link from "next/link";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/page-header";

/**
 * The title of a page opened from Ajustes, with the arrow back to it at the start, where Back
 * always is. `backHref`: another page it was opened from (the IA page from Coach).
 */
export function SubpageHeader({ title, backLabel, backHref = "/ajustes" }: { title: string; backLabel: string; backHref?: string }) {
  return (
    <PageHeader
      title={title}
      start={
        <Button
          variant="ghost"
          size="icon"
          className="-ml-2 size-11"
          nativeButton={false}
          render={<Link href={backHref} aria-label={backLabel} />}
        >
          <ArrowLeftIcon />
        </Button>
      }
    />
  );
}
