"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { HomeIcon, MessageCircleIcon, SettingsIcon, TrendingUpIcon } from "lucide-react";
import { t } from "@/i18n";
import { cn } from "@/lib/utils";

const items = [
  { href: "/", label: t.nav.hoy, icon: HomeIcon },
  { href: "/progreso", label: t.nav.progreso, icon: TrendingUpIcon },
  { href: "/coach", label: t.nav.coach, icon: MessageCircleIcon },
  { href: "/ajustes", label: t.nav.ajustes, icon: SettingsIcon },
];

/**
 * Mobile-first bottom navigation; becomes a top bar on desktop. On desktop it is
 * sticky and moved first in the (flex-column) body, so it takes its own height
 * instead of overlapping the page header.
 */
export function AppNav() {
  const pathname = usePathname();
  if (pathname === "/login" || pathname === "/bienvenida") return null;

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur supports-[backdrop-filter]:bg-background/80 md:sticky md:inset-x-auto md:bottom-auto md:top-0 md:order-first md:w-full md:border-t-0 md:border-b md:pb-0">
      <ul className="mx-auto flex max-w-2xl items-stretch justify-around md:justify-center md:gap-2 md:px-4">
        {items.map((item) => {
          const active =
            item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
          return (
            <li key={item.href} className="flex-1 md:flex-none">
              <Link
                href={item.href}
                className={cn(
                  "flex flex-col items-center gap-0.5 py-2 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground md:flex-row md:gap-2 md:rounded-md md:px-3 md:py-2",
                  active && "text-foreground md:bg-tertiary-container md:text-tertiary-container-foreground",
                )}
              >
                {/* Selected tab: ember pill behind the icon, as in the Android bottom bar. */}
                <span
                  className={cn(
                    "rounded-full px-4 py-0.5 md:p-0",
                    active && "bg-tertiary-container text-tertiary-container-foreground md:bg-transparent",
                  )}
                >
                  <item.icon className="size-5 md:size-4" />
                </span>
                <span>{item.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
