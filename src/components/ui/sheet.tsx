"use client";

import * as React from "react";
import { Dialog as DialogPrimitive } from "@base-ui/react/dialog";
import { XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { t } from "@/i18n";

/** Drag distance (px) past which letting go closes the sheet. */
const DISMISS_DISTANCE = 96;

/**
 * Bottom sheet on phones, centred dialog from `sm` up. On phones it closes by
 * dragging the handle/header down (like a native sheet), by tapping outside or
 * with Escape; the body scrolls on its own so scrolling never fights the drag.
 * `onOpenChange(false)` is the only way it closes, so the parent can ask first
 * (e.g. «¿Descartar los cambios?»).
 */
export function Sheet({
  open,
  onOpenChange,
  title,
  headerAction,
  children,
  className,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: React.ReactNode;
  /** Optional control at the start of the header (e.g. a back button). */
  headerAction?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  const [dragY, setDragY] = React.useState(0);
  const start = React.useRef<number | null>(null);

  function onPointerDown(event: React.PointerEvent) {
    if ((event.target as HTMLElement).closest("button")) return;
    start.current = event.clientY;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  }
  function onPointerMove(event: React.PointerEvent) {
    if (start.current === null) return;
    setDragY(Math.max(event.clientY - start.current, 0));
  }
  function onPointerUp() {
    if (start.current === null) return;
    start.current = null;
    const shouldClose = dragY > DISMISS_DISTANCE;
    setDragY(0);
    if (shouldClose) onOpenChange(false);
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={(next) => onOpenChange(next)}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Backdrop className="fixed inset-0 isolate z-50 bg-black/20 duration-150 supports-backdrop-filter:backdrop-blur-xs data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0" />
        <DialogPrimitive.Popup
          data-slot="sheet"
          style={dragY > 0 ? { transform: `translateY(${dragY}px)`, transition: "none" } : undefined}
          className={cn(
            "fixed inset-x-0 bottom-0 z-50 flex max-h-[92dvh] flex-col rounded-t-2xl bg-popover text-sm text-popover-foreground ring-1 ring-foreground/10 outline-none pb-[env(safe-area-inset-bottom)]",
            "duration-200 data-open:animate-in data-open:slide-in-from-bottom data-closed:animate-out data-closed:slide-out-to-bottom",
            "sm:inset-x-auto sm:bottom-auto sm:top-1/2 sm:left-1/2 sm:w-full sm:max-w-lg sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:pb-0 sm:data-open:zoom-in-95 sm:data-open:slide-in-from-bottom-0 sm:data-closed:zoom-out-95 sm:data-closed:slide-out-to-bottom-0",
            className,
          )}
        >
          <div
            className="shrink-0 touch-none select-none px-4 pt-2 pb-3 sm:pt-4"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
          >
            <div className="mx-auto mb-2 h-1.5 w-10 rounded-full bg-muted-foreground/30 sm:hidden" aria-hidden />
            <div className="flex items-center gap-2">
              {headerAction}
              <DialogPrimitive.Title className="flex-1 text-base font-semibold">{title}</DialogPrimitive.Title>
              <DialogPrimitive.Close render={<Button variant="ghost" size="icon-sm" />}>
                <XIcon />
                <span className="sr-only">{t.common.close}</span>
              </DialogPrimitive.Close>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-4">{children}</div>
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
