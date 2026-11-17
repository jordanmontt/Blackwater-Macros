import type { ReactNode } from "react";

/**
 * Every page's title: centred, as on Android (`CenteredTopAppBar`), with an
 * optional control on each side (Back at the start of a sub-page, a page's own
 * action at the end). The side columns share the free space equally, so the
 * title stays centred whatever is beside it.
 */
export function PageHeader({ title, start, end }: { title: string; start?: ReactNode; end?: ReactNode }) {
  return (
    <header className="grid min-h-11 grid-cols-[1fr_auto_1fr] items-center gap-2">
      <div className="flex justify-start">{start}</div>
      <h1 className="text-center text-lg font-semibold">{title}</h1>
      <div className="flex justify-end">{end}</div>
    </header>
  );
}
