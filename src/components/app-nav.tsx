"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BarChart3Icon, HomeIcon, ScaleIcon, SettingsIcon } from "lucide-react";
import { t } from "@/i18n";
import { cn } from "@/lib/utils";

const items = [
  { href: "/", label: t.nav.hoy, icon: HomeIcon },
  { href: "/peso", label: t.nav.peso, icon: ScaleIcon },
  { href: "/estadisticas", label: t.nav.estadisticas, icon: BarChart3Icon },
  { href: "/ajustes", label: t.nav.ajustes, icon: SettingsIcon },
];

/** Mobile-first bottom navigation; becomes a top bar on desktop. */
export function AppNav() {
  const pathname = usePathname();
  if (pathname === "/login") return null;

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur supports-[backdrop-filter]:bg-background/80 md:inset-x-auto md:bottom-auto md:top-0 md:w-full md:border-t-0 md:border-b md:pb-0">
      <ul className="mx-auto flex max-w-2xl items-stretch justify-around md:justify-start md:gap-2 md:px-4">
        {items.map((item) => {
          const active =
            item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
          return (
            <li key={item.href} className="flex-1 md:flex-none">
              <Link
                href={item.href}
                className={cn(
                  "flex flex-col items-center gap-0.5 py-2 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground md:flex-row md:gap-2 md:rounded-md md:px-3 md:py-2",
                  active && "text-primary",
                )}
              >
                <item.icon className="size-5 md:size-4" />
                <span>{item.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
