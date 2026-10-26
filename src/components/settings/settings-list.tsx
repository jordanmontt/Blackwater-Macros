"use client";

import { useId, type ReactNode } from "react";
import Link from "next/link";
import { ChevronRightIcon, type LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Ajustes as a grouped list (Android `SettingsGroup` / `SettingsRow`): a short heading,
 * then one card of rows, each an icon, a title, an optional line under it and what is
 * on the right (an arrow, a control). Every setting is on the page, one tap away.
 */
export function SettingsGroup({ title, footer, children }: { title: string; footer?: ReactNode; children: ReactNode }) {
  const id = useId();
  return (
    <section aria-labelledby={id} className="space-y-2">
      <h2 id={id} className="px-4 text-sm font-medium text-primary">
        {title}
      </h2>
      <div className="overflow-hidden rounded-xl bg-card py-1 text-card-foreground ring-1 ring-foreground/10">{children}</div>
      {footer ? <div className="space-y-1 px-4 text-xs text-muted-foreground">{footer}</div> : null}
    </section>
  );
}

type RowAction =
  /** A page of the app: an arrow on the right. */
  | { href: string; external?: false; download?: false }
  /** Another site, in a new tab: an arrow on the right. */
  | { href: string; external: true; download?: false }
  /** A file to save: no arrow. */
  | { href: string; download: true; external?: false }
  | { onClick: () => void; expanded?: boolean }
  | Record<never, never>;

type RowProps = {
  icon: LucideIcon;
  /** Extra classes for the icon (a spinner turning). */
  iconClassName?: string;
  title: ReactNode;
  description?: ReactNode;
  /** A control or value on the right; links get an arrow when there is none. */
  trailing?: ReactNode;
  /** Something below the text, inside the row (the theme choice). */
  below?: ReactNode;
  destructive?: boolean;
  disabled?: boolean;
} & RowAction;

export function SettingsRow(props: RowProps) {
  const { icon: Icon, iconClassName, title, description, trailing, below, destructive, disabled } = props;
  const isLink = "href" in props;
  const interactive = isLink || "onClick" in props;
  const arrow = isLink && !props.download && trailing === undefined;
  // Inside a link or a button only phrasing elements are valid; a plain row can hold controls.
  const Box = interactive ? "span" : "div";
  const content = (
    <>
      <Icon aria-hidden className={cn("size-5 shrink-0", destructive ? "text-destructive" : "text-muted-foreground", iconClassName)} />
      <Box className="min-w-0 flex-1">
        <Box className={cn("block text-sm font-medium", destructive && "text-destructive")}>{title}</Box>
        {description ? <Box className="mt-0.5 block text-xs text-muted-foreground">{description}</Box> : null}
        {below ? <Box className="mt-2.5 block">{below}</Box> : null}
      </Box>
      {trailing}
      {arrow ? <ChevronRightIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" /> : null}
    </>
  );
  const className = cn(
    "flex w-full items-center gap-3 px-4 py-3 text-left",
    interactive &&
      "outline-none transition-colors hover:bg-accent focus-visible:bg-accent focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset",
    disabled && "pointer-events-none opacity-50",
  );

  if ("href" in props) {
    if (props.external || props.download) {
      return (
        <a
          href={props.href}
          className={className}
          aria-disabled={disabled || undefined}
          tabIndex={disabled ? -1 : undefined}
          {...(props.external ? { target: "_blank", rel: "noreferrer" } : { download: true })}
        >
          {content}
        </a>
      );
    }
    return (
      <Link href={props.href} className={className}>
        {content}
      </Link>
    );
  }
  if ("onClick" in props) {
    return (
      <button type="button" className={className} disabled={disabled} aria-expanded={props.expanded} onClick={props.onClick}>
        {content}
      </button>
    );
  }
  return <div className={className}>{content}</div>;
}
