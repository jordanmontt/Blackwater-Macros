import Link from "next/link";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/page-header";

/** The title of a page opened from Ajustes, with the arrow back to it at the start, where Back always is. */
export function SubpageHeader({ title, backLabel }: { title: string; backLabel: string }) {
  return (
    <PageHeader
      title={title}
      start={
        <Button
          variant="ghost"
          size="icon"
          className="-ml-2 size-11"
          nativeButton={false}
          render={<Link href="/ajustes" aria-label={backLabel} />}
        >
          <ArrowLeftIcon />
        </Button>
      }
    />
  );
}
