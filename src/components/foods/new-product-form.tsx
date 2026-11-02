"use client";

import { useEffect, useRef, useState } from "react";
import { CameraIcon, Loader2Icon, RefreshCwIcon, XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AiImage } from "@/lib/core/ai-providers";
import {
  newProductPer100g,
  newProductProblems,
  type LabelBasis,
  type NewProduct,
  type NewProductProblem,
} from "@/lib/core/new-product";
import { AiError } from "@/lib/ai/client";
import { downscalePhoto } from "@/lib/ai/images";
import { readLabel } from "@/lib/ai/label";
import { isAiReady, useAiSettings } from "@/lib/ai/settings";
import { formatNumber } from "@/i18n/format";
import { cn, normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { formatTemplate, t } from "@/i18n";

const BASES: { value: LabelBasis; label: string }[] = [
  { value: "100g", label: t.newProduct.basis100g },
  { value: "100ml", label: t.newProduct.basis100ml },
  { value: "serving", label: t.newProduct.basisServing },
];

type Reading = "idle" | "reading" | "done" | "failed";

function numberText(value: number | null): string {
  return value === null ? "" : toDecimalInput(value);
}

function parseNumber(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === "") return null;
  const value = Number(trimmed.replace(",", "."));
  return Number.isFinite(value) ? value : null;
}

/**
 * «Nuevo producto»: the label copied by hand, or read by the AI from a photo
 * (when AI is set up; the photo also goes to Open Food Facts so its volunteers
 * can check the values). `initial`: editing a product added before;
 * `existing`: Open Food Facts already has it without nutrition, so the text says
 * only the values are added.
 */
export function NewProductForm({
  code,
  initial,
  known,
  existing,
  editing,
  onSave,
}: {
  code: string;
  initial: NewProduct | null;
  known: { name: string | null; brand: string | null };
  existing: boolean;
  editing: boolean;
  onSave: (product: NewProduct, photo: AiImage | null) => void;
}) {
  const settings = useAiSettings();
  const aiReady = isAiReady(settings);
  const [name, setName] = useState(initial?.name ?? known.name ?? "");
  const [brand, setBrand] = useState(initial?.brand ?? known.brand ?? "");
  const [basis, setBasis] = useState<LabelBasis>(initial?.basis ?? "100g");
  const [serving, setServing] = useState(initial?.servingSize ?? "");
  const [calories, setCalories] = useState(numberText(initial?.calories ?? null));
  const [protein, setProtein] = useState(numberText(initial?.protein ?? null));
  const [carbs, setCarbs] = useState(numberText(initial?.carbs ?? null));
  const [fat, setFat] = useState(numberText(initial?.fat ?? null));
  const [photo, setPhoto] = useState<AiImage | null>(null);
  const [reading, setReading] = useState<Reading>("idle");
  const [readDetail, setReadDetail] = useState("");
  const [tried, setTried] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const abort = useRef<AbortController | null>(null);

  useEffect(() => () => abort.current?.abort(), []);

  const product: NewProduct = {
    code,
    name,
    brand: brand.trim() === "" ? null : brand,
    basis,
    servingSize: serving.trim() === "" ? null : serving.trim(),
    calories: parseNumber(calories),
    protein: parseNumber(protein),
    carbs: parseNumber(carbs),
    fat: parseNumber(fat),
  };
  const problems = newProductProblems(product);
  const shown = tried ? problems : [];
  const per100 = newProductPer100g(product);
  const canShowPer100 =
    product.calories !== null && product.protein !== null && !problems.includes("serving") && basis === "serving";

  async function addPhoto(file: File | undefined) {
    if (!file) return;
    abort.current?.abort();
    let image: AiImage;
    try {
      image = await downscalePhoto(file);
    } catch {
      setReading("failed");
      setReadDetail("");
      return;
    }
    setPhoto(image);
    if (!aiReady) return;
    const controller = new AbortController();
    abort.current = controller;
    setReading("reading");
    setReadDetail("");
    try {
      const read = await readLabel(image, controller.signal);
      if (controller.signal.aborted) return;
      if (read.name && name.trim() === "") setName(read.name);
      if (read.brand && brand.trim() === "") setBrand(read.brand);
      setBasis(read.basis);
      setServing(read.servingSize ?? "");
      setCalories(numberText(read.calories));
      setProtein(numberText(read.protein));
      setCarbs(numberText(read.carbs));
      setFat(numberText(read.fat));
      setReading("done");
    } catch (error) {
      if (controller.signal.aborted) return;
      setReading("failed");
      setReadDetail(error instanceof AiError ? t.ai.errors[error.kind] : "");
    }
  }

  function error(problem: NewProductProblem) {
    return shown.includes(problem) ? (
      <p className="text-xs text-destructive" role="alert">
        {t.newProduct.errors[problem]}
      </p>
    ) : null;
  }

  const numberField = (id: string, label: string, value: string, set: (v: string) => void, problem?: NewProductProblem) => (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        inputMode="decimal"
        value={value}
        aria-invalid={problem ? shown.includes(problem) : undefined}
        onChange={(event) => set(normalizeDecimal(event.target.value))}
      />
    </div>
  );

  return (
    <form
      className="space-y-4"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        setTried(true);
        if (problems.length === 0 && reading !== "reading") onSave(product, photo);
      }}
    >
      <p className="text-xs text-muted-foreground tabular-nums">{formatTemplate(t.newProduct.barcode, { code })}</p>

      <div className="space-y-2">
        {photo ? (
          <div className="flex items-center gap-3">
            {/* eslint-disable-next-line @next/next/no-img-element -- in-memory data URL */}
            <img
              src={`data:${photo.mimeType};base64,${photo.data}`}
              alt={t.newProduct.labelPhotoAlt}
              className="size-16 shrink-0 rounded-lg border object-cover"
            />
            <div className="min-w-0 space-y-1 text-sm">
              <p className={cn("text-muted-foreground", reading === "failed" && "text-destructive")} role="status">
                {reading === "reading" ? (
                  <span className="inline-flex items-center gap-1.5">
                    <Loader2Icon className="size-3.5 animate-spin" /> {t.newProduct.reading}
                  </span>
                ) : reading === "done" ? (
                  t.newProduct.readDone
                ) : reading === "failed" ? (
                  `${t.newProduct.readFailed}${readDetail ? ` ${readDetail}` : ""}`
                ) : (
                  t.newProduct.labelPhotoHint
                )}
              </p>
              <div className="flex gap-1">
                <Button type="button" variant="ghost" size="sm" onClick={() => fileInput.current?.click()}>
                  <RefreshCwIcon /> {t.newProduct.retake}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    abort.current?.abort();
                    setPhoto(null);
                    setReading("idle");
                  }}
                >
                  <XIcon /> {t.newProduct.removePhoto}
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <>
            <Button type="button" variant="outline" className="w-full" onClick={() => fileInput.current?.click()}>
              <CameraIcon /> {t.newProduct.labelPhoto}
            </Button>
            <p className="text-xs text-muted-foreground">
              {aiReady ? t.newProduct.labelPhotoHintAi : t.newProduct.labelPhotoHint}
            </p>
          </>
        )}
        <input
          ref={fileInput}
          type="file"
          accept="image/*"
          capture="environment"
          hidden
          data-testid="label-photo-input"
          onChange={(event) => {
            void addPhoto(event.target.files?.[0]);
            event.target.value = "";
          }}
        />
      </div>

      <div className="space-y-1">
        <Label htmlFor="product-name">{t.newProduct.name}</Label>
        <Input
          id="product-name"
          placeholder={t.newProduct.namePlaceholder}
          value={name}
          aria-invalid={shown.includes("name") || undefined}
          onChange={(event) => setName(event.target.value)}
        />
        {error("name")}
      </div>
      <div className="space-y-1">
        <Label htmlFor="product-brand">
          {t.newProduct.brand} <span className="font-normal text-muted-foreground">({t.newProduct.optional})</span>
        </Label>
        <Input id="product-brand" value={brand} onChange={(event) => setBrand(event.target.value)} />
      </div>

      <div className="space-y-1.5">
        <p className="text-sm font-medium" id="basis-label">
          {t.newProduct.basisLabel}
        </p>
        <div className="grid grid-cols-3 gap-1 rounded-lg border p-1" role="group" aria-labelledby="basis-label">
          {BASES.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={basis === option.value}
              onClick={() => setBasis(option.value)}
              className={cn(
                "rounded-md px-2 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent",
                basis === option.value && "bg-primary text-primary-foreground hover:bg-primary",
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="space-y-1">
        <Label htmlFor="product-serving">
          {t.newProduct.servingSize}
          {basis === "serving" ? null : (
            <span className="font-normal text-muted-foreground"> ({t.newProduct.optional})</span>
          )}
        </Label>
        <Input
          id="product-serving"
          placeholder={t.newProduct.servingPlaceholder}
          value={serving}
          aria-invalid={shown.includes("serving") || undefined}
          onChange={(event) => setServing(event.target.value)}
        />
        {error("serving")}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1">
          {numberField("product-kcal", t.newProduct.calories, calories, setCalories, "calories")}
          {error("calories")}
        </div>
        <div className="space-y-1">
          {numberField("product-protein", t.newProduct.protein, protein, setProtein, "protein")}
          {error("protein")}
        </div>
        {numberField("product-carbs", t.newProduct.carbs, carbs, setCarbs)}
        {numberField("product-fat", t.newProduct.fat, fat, setFat)}
      </div>
      {canShowPer100 ? (
        <p className="text-xs text-muted-foreground" data-testid="per-100-line">
          {formatTemplate(t.newProduct.per100, {
            kcal: formatNumber(per100.calories),
            p: formatNumber(per100.protein, 1),
            unit: /ml/i.test(serving) ? "ml" : "g",
          })}
        </p>
      ) : null}
      {error("tooHigh")}

      <p className="border-t pt-3 text-xs text-muted-foreground">
        {existing ? t.newProduct.consentExisting : t.newProduct.consent}
      </p>
      <Button type="submit" className="w-full" disabled={reading === "reading"}>
        {editing ? t.newProduct.saveEdit : t.newProduct.save}
      </Button>
    </form>
  );
}
