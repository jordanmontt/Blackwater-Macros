"use client";

import { useEffect, useRef, useState } from "react";
import {
  CameraIcon,
  ChevronDownIcon,
  ImageIcon,
  ImagePlusIcon,
  Loader2Icon,
  Maximize2Icon,
  SparklesIcon,
  XIcon,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AiImage } from "@/lib/core/ai-providers";
import {
  correctionBaseline,
  correctionChanges,
  newProductPer100g,
  newProductProblems,
  type LabelBasis,
  type LabelValues,
  type NewProduct,
  type NewProductProblem,
  type OffCorrection,
  PACKAGE_LANGS,
} from "@/lib/core/new-product";
import { AiError } from "@/lib/ai/client";
import { downscalePhoto } from "@/lib/ai/images";
import { readLabel } from "@/lib/ai/label";
import { isAiReady, useAiSettings } from "@/lib/ai/settings";
import { formatNumber } from "@/i18n/format";
import { cn, normalizeDecimal, toDecimalInput } from "@/lib/utils";
import { NativeSelect } from "@/components/ui/native-select";
import { LabelPhotoViewer } from "@/components/foods/label-photo-viewer";
import { currentLanguage, formatTemplate, t } from "@/i18n";

const BASES: { value: LabelBasis; label: string }[] = [
  { value: "100g", label: t.newProduct.basis100g },
  { value: "100ml", label: t.newProduct.basis100ml },
  { value: "serving", label: t.newProduct.basisServing },
];

type Reading = "idle" | "reading" | "done" | "failed";

/** A label photo the form didn't take: one waiting on this device, or the one Open Food Facts shows. */
export interface SavedLabelPhoto {
  src: string;
  from: "device" | "off";
}

/** «Español», in the app's language. */
function languageName(code: string): string {
  try {
    const name = new Intl.DisplayNames([currentLanguage()], { type: "language" }).of(code) ?? code;
    return name.charAt(0).toLocaleUpperCase() + name.slice(1);
  } catch {
    return code;
  }
}

/** Where Save takes the cursor when a value is missing or wrong (the fields' order). */
const PROBLEM_FIELD: Record<NewProductProblem, string> = {
  name: "product-name",
  serving: "product-serving",
  calories: "product-kcal",
  protein: "product-protein",
  tooHigh: "product-kcal",
};

/** The saved photo as the AI takes it: the one waiting here is a data URL; Open Food Facts' is downloaded. */
async function savedPhotoImage(src: string, signal: AbortSignal): Promise<AiImage> {
  const inline = /^data:([^;,]+);base64,(.+)$/.exec(src);
  if (inline) return { mimeType: inline[1], data: inline[2] };
  const response = await fetch(src, { signal });
  if (!response.ok) throw new Error(`label photo: ${response.status}`);
  const blob = await response.blob();
  return downscalePhoto(new File([blob], "label.jpg", { type: blob.type || "image/jpeg" }));
}

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
 * when asked («Rellenar con la IA», since an AI on the device can take a
 * while). The photo also goes to Open Food Facts so its volunteers can check
 * the values, so the form asks for it, and Save asks once more without one
 * (not when editing). `initial`: editing a product added before;
 * `existing`: Open Food Facts already has it without nutrition, so the text says
 * only the values are added. `correction` («Corregir valores»): Open Food Facts
 * has it with values that don't match the label; the form starts from them,
 * keeps the name and brand, and shows what Open Food Facts had next to each
 * changed value. `savedPhoto`: the label photo already there (waiting to be sent,
 * or on Open Food Facts), shown until a new one replaces it. `lang`: the
 * package's language when known; a new product asks for it («Idioma del
 * envase», the app's language to start, or what the AI read), since Open Food
 * Facts keeps names and label photos per language.
 */
export function NewProductForm({
  code,
  initial,
  known,
  lang: knownLang = null,
  existing,
  correction = null,
  savedPhoto = null,
  editing,
  onSave,
}: {
  code: string;
  initial: NewProduct | null;
  known: { name: string | null; brand: string | null };
  lang?: string | null;
  existing: boolean;
  correction?: OffCorrection | null;
  savedPhoto?: SavedLabelPhoto | null;
  editing: boolean;
  onSave: (product: NewProduct, photo: AiImage | null, lang: string) => void;
}) {
  const settings = useAiSettings();
  const aiReady = isAiReady(settings);
  const start = initial ?? correction?.base ?? null;
  const [name, setName] = useState(start?.name ?? known.name ?? "");
  const [brand, setBrand] = useState(start?.brand ?? known.brand ?? "");
  const [basis, setBasis] = useState<LabelBasis>(start?.basis ?? "100g");
  const [serving, setServing] = useState(start?.servingSize ?? "");
  const [calories, setCalories] = useState(numberText(start?.calories ?? null));
  const [protein, setProtein] = useState(numberText(start?.protein ?? null));
  const [carbs, setCarbs] = useState(numberText(start?.carbs ?? null));
  const [fat, setFat] = useState(numberText(start?.fat ?? null));
  const [lang, setLang] = useState(knownLang ?? currentLanguage());
  const langs = PACKAGE_LANGS.includes(lang as (typeof PACKAGE_LANGS)[number]) ? [...PACKAGE_LANGS] : [lang, ...PACKAGE_LANGS];
  const [photo, setPhoto] = useState<AiImage | null>(null);
  const [reading, setReading] = useState<Reading>("idle");
  const [readDetail, setReadDetail] = useState("");
  const [tried, setTried] = useState(false);
  const [askPhoto, setAskPhoto] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const galleryInput = useRef<HTMLInputElement>(null);
  const photoReminder = useRef<HTMLDivElement>(null);
  const [viewing, setViewing] = useState(false);
  const abort = useRef<AbortController | null>(null);

  useEffect(() => () => abort.current?.abort(), []);
  // The reminder is taller than the Save button it replaces: bring its buttons into view.
  useEffect(() => {
    if (!askPhoto) return;
    const frame = requestAnimationFrame(() => photoReminder.current?.scrollIntoView?.({ block: "nearest" }));
    return () => cancelAnimationFrame(frame);
  }, [askPhoto]);

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
  const changes = correction ? correctionChanges(correction, product) : [];
  const baseline = correction ? correctionBaseline(correction, product) : null;
  // A correction that changes nothing and brings no photo would only repeat what Open Food Facts has.
  const unchanged = correction !== null && changes.length === 0 && photo === null;
  const problems = newProductProblems(product, correction?.per ?? null);
  const shown = tried ? problems : [];
  const per100 = newProductPer100g(product);
  const canShowPer100 =
    product.calories !== null && product.protein !== null && !problems.includes("serving") && basis === "serving";

  // The photo shown: a new one, or else the one already there; and what it is.
  const shownPhoto = photo ? `data:${photo.mimeType};base64,${photo.data}` : (savedPhoto?.src ?? null);
  const photoSource = photo
    ? t.newProduct.photoSentOnSave
    : savedPhoto?.from === "device"
      ? t.newProduct.photoWaiting
      : t.newProduct.photoOnOff;

  const openCamera = () => fileInput.current?.click();
  const openGallery = () => galleryInput.current?.click();

  function removePhoto() {
    abort.current?.abort();
    setPhoto(null);
    setReading("idle");
  }

  async function addPhoto(file: File | undefined) {
    if (!file) return;
    abort.current?.abort();
    setReading("idle");
    let image: AiImage;
    try {
      image = await downscalePhoto(file);
    } catch {
      setReading("failed");
      setReadDetail("");
      return;
    }
    setPhoto(image);
    setAskPhoto(false);
  }

  /** «Rellenar con la IA»: the new photo, or else the saved one. */
  async function fillWithAi() {
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setReading("reading");
    setReadDetail("");
    try {
      const image = photo ?? (await savedPhotoImage(savedPhoto!.src, controller.signal));
      const read = await readLabel(image, controller.signal);
      if (controller.signal.aborted) return;
      if (read.name && name.trim() === "") setName(read.name);
      if (read.brand && brand.trim() === "") setBrand(read.brand);
      // An existing product keeps the language Open Food Facts has for it.
      if (read.language && !existing) setLang(read.language);
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

  function cancelReading() {
    abort.current?.abort();
    setReading("idle");
  }

  /** Save, or show what is missing and take the cursor to the first one. */
  function save(withoutPhoto: boolean) {
    setTried(true);
    if (problems.length > 0) {
      const field = document.getElementById(PROBLEM_FIELD[problems[0]]);
      field?.focus();
      field?.scrollIntoView?.({ block: "center" });
      return;
    }
    if (unchanged || reading === "reading") return;
    if (!withoutPhoto && !editing && photo === null && savedPhoto === null) {
      setAskPhoto(true);
      return;
    }
    onSave(product, photo, lang);
  }

  function error(problem: NewProductProblem) {
    return shown.includes(problem) ? (
      <p className="text-xs text-destructive" role="alert">
        {t.newProduct.errors[problem]}
      </p>
    ) : null;
  }

  const numberField = (
    id: string,
    label: string,
    value: string,
    set: (v: string) => void,
    nutrient: keyof LabelValues,
    problem?: NewProductProblem,
  ) => {
    const before = baseline?.[nutrient] ?? null;
    const hint = changes.includes(nutrient) && before !== null;
    return (
      <div className="space-y-1">
        <Label htmlFor={id}>{label}</Label>
        <Input
          id={id}
          inputMode="decimal"
          value={value}
          aria-invalid={problem ? shown.includes(problem) : undefined}
          aria-describedby={hint ? `${id}-off` : undefined}
          onChange={(event) => set(normalizeDecimal(event.target.value))}
        />
        {hint ? (
          <p id={`${id}-off`} className="text-xs text-muted-foreground tabular-nums">
            {formatTemplate(t.newProduct.offValue, { value: formatNumber(before, nutrient === "calories" ? 0 : 1) })}
          </p>
        ) : null}
      </div>
    );
  };

  return (
    <form
      className="space-y-4"
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        save(false);
      }}
    >
      <div className="space-y-1">
        {correction ? (
          <p className="font-medium">
            {name}
            {brand ? <span className="font-normal text-muted-foreground"> · {brand}</span> : null}
          </p>
        ) : null}
        <p className="text-xs text-muted-foreground tabular-nums">{formatTemplate(t.newProduct.barcode, { code })}</p>
        {correction ? <p className="text-xs text-muted-foreground">{t.newProduct.correctCheck}</p> : null}
      </div>

      <div className="space-y-2">
        {shownPhoto ? (
          <div className="space-y-2">
            <div className="flex items-start gap-3">
              <button
                type="button"
                onClick={() => setViewing(true)}
                aria-label={t.newProduct.viewPhoto}
                className="relative size-19 shrink-0 overflow-hidden rounded-lg border outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
              >
                {/* eslint-disable-next-line @next/next/no-img-element -- an in-memory data URL or Open Food Facts' image */}
                <img
                  src={shownPhoto}
                  alt=""
                  className="size-full object-cover"
                  data-testid={photo ? undefined : "saved-label-photo"}
                />
                <span className="absolute right-1 bottom-1 flex size-6 items-center justify-center rounded-full border bg-background/90 text-foreground">
                  <Maximize2Icon className="size-3.5" />
                </span>
              </button>
              <div className="min-w-0 space-y-0.5 text-sm">
                <p className="font-medium">{photo || savedPhoto?.from === "device" ? t.newProduct.yourPhoto : t.newProduct.offPhoto}</p>
                <p className={cn("text-xs text-muted-foreground", reading === "failed" && "text-destructive")} role="status">
                  {reading === "reading" ? (
                    <span className="inline-flex items-center gap-1.5">
                      <Loader2Icon className="size-3.5 animate-spin" /> {t.newProduct.reading}
                    </span>
                  ) : reading === "done" ? (
                    t.newProduct.readDone
                  ) : reading === "failed" ? (
                    `${t.newProduct.readFailed}${readDetail ? ` ${readDetail}` : ""}`
                  ) : (
                    photoSource
                  )}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap gap-1">
              {aiReady && reading === "reading" ? (
                <Button type="button" variant="ghost" onClick={cancelReading}>
                  <XIcon /> {t.meal.cancel}
                </Button>
              ) : aiReady && reading !== "done" ? (
                <Button type="button" variant="secondary" onClick={() => void fillWithAi()}>
                  <SparklesIcon /> {reading === "failed" ? t.common.retry : t.newProduct.fillWithAi}
                </Button>
              ) : null}
              <DropdownMenu>
                <DropdownMenuTrigger render={<Button type="button" variant="ghost" />}>
                  <ImagePlusIcon /> {t.newProduct.changePhoto} <ChevronDownIcon />
                </DropdownMenuTrigger>
                <DropdownMenuContent>
                  <DropdownMenuItem onClick={openCamera}>
                    <CameraIcon /> {t.photo.camera}
                  </DropdownMenuItem>
                  <DropdownMenuItem onClick={openGallery}>
                    <ImageIcon /> {t.newProduct.chooseFromGallery}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
              {photo ? (
                <Button type="button" variant="ghost" className="text-muted-foreground" onClick={removePhoto}>
                  <XIcon /> {t.newProduct.removePhoto}
                </Button>
              ) : null}
            </div>
            <LabelPhotoViewer
              open={viewing}
              onOpenChange={setViewing}
              src={shownPhoto}
              source={photo || savedPhoto?.from === "device" ? photoSource : t.newProduct.fromOff}
              onCamera={openCamera}
              onGallery={openGallery}
              onRemove={photo ? removePhoto : undefined}
            />
          </div>
        ) : (
          <div className="space-y-3 rounded-xl border border-dashed border-primary/50 bg-primary/5 p-4">
            <div className="flex items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <CameraIcon className="size-5" />
              </span>
              <div className="min-w-0 space-y-0.5">
                <p className="text-sm font-medium">
                  {t.newProduct.photoTitle}{" "}
                  <span className="ml-0.5 inline-block rounded-full bg-primary/15 px-2 py-0.5 text-xs font-medium text-primary">
                    {t.newProduct.recommended}
                  </span>
                </p>
                <p className={cn("text-xs text-muted-foreground", reading === "failed" && "text-destructive")}>
                  {reading === "failed"
                    ? t.newProduct.readFailed
                    : aiReady
                      ? t.newProduct.labelPhotoHintAi
                      : t.newProduct.labelPhotoHint}
                </p>
              </div>
            </div>
            <div className="flex gap-2">
              <Button type="button" className="flex-1" onClick={openCamera}>
                <CameraIcon /> {t.photo.camera}
              </Button>
              <Button type="button" variant="outline" onClick={openGallery}>
                <ImageIcon /> {t.photo.gallery}
              </Button>
            </div>
          </div>
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
        <input
          ref={galleryInput}
          type="file"
          accept="image/*"
          hidden
          data-testid="label-photo-gallery"
          onChange={(event) => {
            void addPhoto(event.target.files?.[0]);
            event.target.value = "";
          }}
        />
      </div>

      {correction ? null : (
        <>
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
          {existing ? null : (
            <div className="space-y-1">
              <Label htmlFor="product-lang">{t.newProduct.packageLang}</Label>
              <NativeSelect id="product-lang" value={lang} onChange={(event) => setLang(event.target.value)}>
                {langs.map((code) => (
                  <option key={code} value={code}>
                    {languageName(code)}
                  </option>
                ))}
              </NativeSelect>
              <p className="text-xs text-muted-foreground">{t.newProduct.packageLangHint}</p>
            </div>
          )}
        </>
      )}

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
          {numberField("product-kcal", t.newProduct.calories, calories, setCalories, "calories", "calories")}
          {error("calories")}
        </div>
        <div className="space-y-1">
          {numberField("product-protein", t.newProduct.protein, protein, setProtein, "protein", "protein")}
          {error("protein")}
        </div>
        {numberField("product-carbs", t.newProduct.carbs, carbs, setCarbs, "carbs")}
        {numberField("product-fat", t.newProduct.fat, fat, setFat, "fat")}
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

      {tried && unchanged && problems.length === 0 ? (
        <p className="text-xs text-destructive" role="alert">
          {t.newProduct.errors.unchanged}
        </p>
      ) : null}

      <p className="border-t pt-3 text-xs text-muted-foreground">
        {correction ? t.newProduct.consentCorrection : existing ? t.newProduct.consentExisting : t.newProduct.consent}
      </p>
      {askPhoto ? (
        <div ref={photoReminder} className="space-y-3 rounded-xl border border-primary/50 bg-primary/5 p-3">
          <p className="text-sm" role="alert">
            {t.newProduct.noPhoto}
          </p>
          <div className="flex gap-2">
            <Button type="button" className="flex-1" onClick={openCamera}>
              <CameraIcon /> {t.photo.camera}
            </Button>
            <Button type="button" variant="outline" className="flex-1" onClick={openGallery}>
              <ImageIcon /> {t.photo.gallery}
            </Button>
          </div>
          <Button type="button" variant="ghost" className="w-full text-muted-foreground" onClick={() => save(true)}>
            {t.newProduct.saveWithoutPhoto}
          </Button>
        </div>
      ) : (
        <Button type="submit" className="w-full" disabled={reading === "reading"}>
          {editing ? t.newProduct.saveEdit : t.newProduct.save}
        </Button>
      )}
    </form>
  );
}
