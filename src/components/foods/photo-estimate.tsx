"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { CameraIcon, ImagesIcon, Loader2Icon, SparklesIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { AiImage } from "@/lib/core/ai-providers";
import type { MealEstimate } from "@/lib/core/ai-schema";
import { AiError } from "@/lib/ai/client";
import { estimateMeal } from "@/lib/ai/estimate";
import { downscalePhoto, MAX_PHOTOS } from "@/lib/ai/images";
import { isAiReady, useAiSettings } from "@/lib/ai/settings";
import { formatTemplate, t } from "@/i18n";

interface Photo {
  id: number;
  image: AiImage;
}

/**
 * «Foto»: up to five photos (camera or gallery) and an optional description →
 * the AI's estimate for the review form. Photos live only in this component's
 * memory (downscaled JPEGs); closing the sheet drops them.
 * `autoDescription`: «Estimar “…” con IA» from search starts right away.
 */
export function PhotoEstimate({
  onEstimate,
  onManual,
  autoDescription = null,
}: {
  onEstimate: (estimate: MealEstimate) => void;
  onManual?: () => void;
  autoDescription?: string | null;
}) {
  const settings = useAiSettings();
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [description, setDescription] = useState(autoDescription ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errorDetail, setErrorDetail] = useState("");
  const cameraInput = useRef<HTMLInputElement>(null);
  const galleryInput = useRef<HTMLInputElement>(null);
  const abort = useRef<AbortController | null>(null);
  const nextId = useRef(1);
  const started = useRef(false);

  useEffect(() => () => abort.current?.abort(), []);

  async function run(text: string, images: AiImage[]) {
    if (images.length === 0 && text.trim() === "") {
      setError(t.photo.needInput);
      return;
    }
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setBusy(true);
    setError(null);
    setErrorDetail("");
    try {
      const estimate = await estimateMeal({ description: text, photos: images }, controller.signal);
      if (!controller.signal.aborted) onEstimate(estimate);
    } catch (caught) {
      if (controller.signal.aborted) return;
      const detail = caught instanceof AiError ? caught.detail : caught instanceof Error ? caught.message.slice(0, 200) : "";
      setError(t.ai.errors[caught instanceof AiError ? caught.kind : "provider"]);
      setErrorDetail(detail);
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  const ready = isAiReady(settings);

  useEffect(() => {
    if (!ready || !autoDescription || started.current) return;
    started.current = true;
    void run(autoDescription, []);
    return () => {
      // Strict Mode unmounts once in development: the call above was aborted, start again.
      abort.current?.abort();
      started.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, autoDescription]);

  if (!ready) {
    return (
      <div className="space-y-3 rounded-xl border p-4 text-sm">
        <p>{t.photo.notConfigured}</p>
        <div className="flex flex-wrap gap-2">
          <Button nativeButton={false} render={<Link href="/ajustes" />}>
            <SparklesIcon /> {t.photo.configure}
          </Button>
          {onManual ? (
            <Button variant="outline" onClick={onManual}>
              {t.addFood.manual}
            </Button>
          ) : null}
        </div>
      </div>
    );
  }

  async function addFiles(files: FileList | null) {
    if (!files) return;
    setError(null);
    const room = MAX_PHOTOS - photos.length;
    const added: Photo[] = [];
    for (const file of Array.from(files).slice(0, room)) {
      try {
        added.push({ id: nextId.current++, image: await downscalePhoto(file) });
      } catch {
        setError(t.photo.photoError);
      }
    }
    setPhotos((current) => [...current, ...added].slice(0, MAX_PHOTOS));
  }

  const full = photos.length >= MAX_PHOTOS;

  return (
    <div className="space-y-4">
      <section aria-label={t.photo.tipsTitle} className="rounded-xl border bg-primary/5 p-3 text-sm">
        <h3 className="font-medium">{t.photo.tipsTitle}</h3>
        <ul className="mt-1.5 list-disc space-y-0.5 pl-5 text-muted-foreground">
          {t.photo.tips.map((tip) => (
            <li key={tip}>{tip}</li>
          ))}
        </ul>
      </section>

      {photos.length > 0 ? (
        <ul className="flex gap-2 overflow-x-auto pb-1">
          {photos.map((photo, index) => (
            <li key={photo.id} className="relative shrink-0">
              {/* eslint-disable-next-line @next/next/no-img-element -- in-memory data URL, never uploaded */}
              <img
                src={`data:${photo.image.mimeType};base64,${photo.image.data}`}
                alt={formatTemplate(t.photo.photoAlt, { n: index + 1 })}
                className="size-20 rounded-lg border object-cover"
              />
              <button
                type="button"
                aria-label={formatTemplate(t.photo.removePhoto, { n: index + 1 })}
                disabled={busy}
                onClick={() => setPhotos((current) => current.filter((item) => item.id !== photo.id))}
                className="absolute -top-1.5 -right-1.5 flex size-6 items-center justify-center rounded-full border bg-background shadow-sm"
              >
                <XIcon className="size-3.5" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      <div className="grid grid-cols-2 gap-2">
        <Button variant="outline" disabled={full || busy} onClick={() => cameraInput.current?.click()}>
          <CameraIcon /> {t.photo.camera}
        </Button>
        <Button variant="outline" disabled={full || busy} onClick={() => galleryInput.current?.click()}>
          <ImagesIcon /> {t.photo.gallery}
        </Button>
        <input
          ref={cameraInput}
          type="file"
          accept="image/*"
          capture="environment"
          hidden
          data-testid="photo-camera-input"
          onChange={(event) => {
            void addFiles(event.target.files);
            event.target.value = "";
          }}
        />
        <input
          ref={galleryInput}
          type="file"
          accept="image/*"
          multiple
          hidden
          data-testid="photo-gallery-input"
          onChange={(event) => {
            void addFiles(event.target.files);
            event.target.value = "";
          }}
        />
      </div>
      <p className="text-xs text-muted-foreground">
        {formatTemplate(t.photo.maxPhotos, { n: MAX_PHOTOS, provider: t.ai.providerShort[settings.provider] })}
      </p>

      <div className="space-y-1.5">
        <Label htmlFor="photo-description">{t.photo.describe}</Label>
        <Textarea
          id="photo-description"
          rows={2}
          placeholder={t.photo.describePlaceholder}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </div>

      {error ? (
        <div role="alert" className="space-y-2 rounded-lg border border-destructive/40 p-3 text-sm text-destructive">
          <p>{error}</p>
          {errorDetail ? <p className="text-xs text-muted-foreground">{errorDetail}</p> : null}
          {onManual ? (
            <Button variant="outline" size="sm" onClick={onManual}>
              {t.addFood.manual}
            </Button>
          ) : null}
        </div>
      ) : null}

      <Button className="w-full" disabled={busy} onClick={() => void run(description, photos.map((photo) => photo.image))}>
        {busy ? <Loader2Icon className="animate-spin" /> : <SparklesIcon />}
        {busy ? t.photo.estimating : t.photo.estimate}
      </Button>
    </div>
  );
}

/** The line above the review form for an AI estimate. */
export function estimateNotice(estimate: MealEstimate): string {
  const base = estimate.confidence
    ? formatTemplate(t.photo.notice, { confidence: t.photo.confidence[estimate.confidence] })
    : t.photo.noticePlain;
  return estimate.notes ? `${base} ${estimate.notes}` : base;
}
