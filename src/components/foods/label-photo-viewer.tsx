"use client";

import { useState } from "react";
import { CameraIcon, ImageIcon, XIcon, ZoomInIcon, ZoomOutIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { t } from "@/i18n";

/**
 * The label photo at full size, to check it before saving: Open Food Facts' one
 * matches the package, or yours is sharp. Nutrition labels are small print, so
 * it zooms (the button, or a double tap) and pans by scrolling. From here the
 * photo can also be changed, or removed when it is yours.
 */
export function LabelPhotoViewer({
  open,
  onOpenChange,
  src,
  source,
  onCamera,
  onGallery,
  onRemove,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  src: string;
  /** Where the photo comes from, under the title. */
  source: string;
  onCamera: () => void;
  onGallery: () => void;
  /** Only for a photo the form took (not sent yet). */
  onRemove?: () => void;
}) {
  const [zoomed, setZoomed] = useState(false);

  function then(action: () => void) {
    onOpenChange(false);
    action();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setZoomed(false);
        onOpenChange(next);
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="grid h-dvh max-h-dvh max-w-none grid-rows-[auto_minmax(0,1fr)_auto] gap-0 rounded-none bg-neutral-950 p-0 text-neutral-50 ring-0 sm:h-auto sm:max-h-[90dvh] sm:max-w-2xl sm:rounded-xl"
      >
        <div className="flex items-center gap-2 px-2 py-2">
          <DialogClose
            render={
              <Button
                variant="ghost"
                size="icon"
                className="size-11 text-neutral-50 hover:bg-white/10 hover:text-neutral-50"
                aria-label={t.common.close}
              />
            }
          >
            <XIcon />
          </DialogClose>
          <div className="min-w-0 flex-1">
            <DialogTitle className="text-neutral-50">{t.newProduct.viewerTitle}</DialogTitle>
            <DialogDescription className="mt-1 truncate text-xs text-neutral-400">{source}</DialogDescription>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="size-11 text-neutral-50 hover:bg-white/10 hover:text-neutral-50"
            aria-pressed={zoomed}
            aria-label={zoomed ? t.newProduct.zoomOut : t.newProduct.zoomIn}
            onClick={() => setZoomed((z) => !z)}
          >
            {zoomed ? <ZoomOutIcon /> : <ZoomInIcon />}
          </Button>
        </div>
        <div className="min-h-0 overflow-auto overscroll-contain" data-testid="label-photo-stage">
          {/* eslint-disable-next-line @next/next/no-img-element -- an in-memory data URL or Open Food Facts' image */}
          <img
            src={src}
            alt={t.newProduct.labelPhotoAlt}
            onDoubleClick={() => setZoomed((z) => !z)}
            className={
              zoomed
                ? "w-[250%] max-w-none cursor-zoom-out"
                : "mx-auto h-full max-h-full w-full cursor-zoom-in object-contain sm:max-h-[65dvh]"
            }
          />
        </div>
        <div className="flex flex-wrap items-center gap-2 p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
          <Button
            type="button"
            size="lg"
            variant="outline"
            className="border-white/25 bg-transparent text-neutral-50 hover:bg-white/10 hover:text-neutral-50"
            onClick={() => then(onCamera)}
          >
            <CameraIcon /> {t.photo.camera}
          </Button>
          <Button
            type="button"
            size="lg"
            variant="outline"
            className="border-white/25 bg-transparent text-neutral-50 hover:bg-white/10 hover:text-neutral-50"
            onClick={() => then(onGallery)}
          >
            <ImageIcon /> {t.photo.gallery}
          </Button>
          {onRemove ? (
            <Button
              type="button"
              size="lg"
              variant="ghost"
              className="text-neutral-300 hover:bg-white/10 hover:text-neutral-50"
              onClick={() => then(onRemove)}
            >
              <XIcon /> {t.newProduct.removePhoto}
            </Button>
          ) : null}
          <DialogClose render={<Button size="lg" className="ml-auto bg-neutral-50 text-neutral-950 hover:bg-neutral-200" />}>
            {t.newProduct.done}
          </DialogClose>
        </div>
      </DialogContent>
    </Dialog>
  );
}
