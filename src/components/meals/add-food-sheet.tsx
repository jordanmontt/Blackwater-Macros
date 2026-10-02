"use client";

import { useState } from "react";
import { formatDateKeyShort, formatNumber } from "@/i18n/format";
import { ArrowLeftIcon, CameraIcon, CopyIcon, PencilLineIcon, ScanBarcodeIcon, SearchIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Sheet } from "@/components/ui/sheet";
import { DayNavigator } from "@/components/meals/day-navigator";
import { BarcodeScanner } from "@/components/foods/barcode-scanner";
import { FoodSearch } from "@/components/foods/food-search";
import { PhotoEstimate } from "@/components/foods/photo-estimate";
import { PortionPicker } from "@/components/foods/portion-picker";
import { productChoice, rememberFood, type FoodChoice } from "@/lib/foods/foods-client";
import { foodToIngredient } from "@/lib/core/foods";
import type { MealEstimate } from "@/lib/core/ai-schema";
import { api } from "@/lib/api";
import { useCachedResource } from "@/lib/use-cached-resource";
import { addDaysToKey } from "@/lib/core/dates";
import { copyMealPayload } from "@/lib/meal-payload";
import type { IngredientInput, MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

type View = "menu" | "copy" | "search" | "barcode" | "portion" | "photo";

/**
 * «Añadir comida»: every way to add food to `day`. Buscar and Código de barras
 * pick a food and its portion, then hand it over (`onFoodPicked`) for the
 * review form; Escribir a mano opens that form empty; Copiar de otro día and
 * the templates add meals directly (with Undo). `pickOnly`: opened from the
 * review form to add one more food, so only Buscar and Código are offered.
 */
export function AddFoodSheet({
  open,
  onOpenChange,
  day,
  templates,
  onManual,
  onAdded,
  onFoodPicked,
  onEstimate,
  pickOnly = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  day: string;
  templates: MealTemplateDTO[];
  onManual: () => void;
  /** Meals were created on `day` (copies or a template). */
  onAdded: () => void | Promise<unknown>;
  /** A food and portion picked from search or a barcode. */
  onFoodPicked: (ingredient: IngredientInput) => void;
  /** The AI estimated a meal from photos or a description. */
  onEstimate: (estimate: MealEstimate) => void;
  pickOnly?: boolean;
}) {
  const [view, setView] = useState<View>("menu");
  const [choice, setChoice] = useState<FoodChoice | null>(null);
  const [back, setBack] = useState<View>("menu");
  /** Set when the photo view was opened by «Estimar “…” con IA» from search. */
  const [estimateQuery, setEstimateQuery] = useState<string | null>(null);

  function pick(next: FoodChoice, from: View) {
    setChoice(next);
    setBack(from);
    setView("portion");
  }
  const [busy, setBusy] = useState(false);

  function changeOpen(next: boolean) {
    if (!next) {
      setView("menu");
      setChoice(null);
      setEstimateQuery(null);
    }
    onOpenChange(next);
  }

  const titles: Record<View, string> = {
    menu: t.addFood.title,
    copy: t.addFood.copy,
    search: t.addFood.search,
    barcode: t.addFood.barcode,
    portion: t.addFood.portionTitle,
    photo: t.photo.title,
  };

  /** Creates the meals, closes the sheet and offers Undo. */
  async function addMeals(sources: Parameters<typeof copyMealPayload>[0][], message: string) {
    setBusy(true);
    try {
      const created: MealDTO[] = [];
      for (const source of sources) created.push(await api.createMeal(copyMealPayload(source, day)));
      changeOpen(false);
      await onAdded();
      toast.success(message, {
        action: {
          label: t.addFood.undo,
          onClick: () => {
            void Promise.all(created.map((meal) => api.deleteMeal(meal.id))).then(() => onAdded());
          },
        },
      });
    } catch {
      toast.error(t.common.errorGeneric);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Sheet
      open={open}
      onOpenChange={changeOpen}
      title={titles[view]}
      headerAction={
        view !== "menu" ? (
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t.addFood.back}
            onClick={() => {
              setView(view === "portion" ? back : view === "photo" && estimateQuery !== null ? "search" : "menu");
              setEstimateQuery(null);
            }}
          >
            <ArrowLeftIcon />
          </Button>
        ) : undefined
      }
    >
      {view === "menu" ? (
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-2">
            <BigSourceButton
              icon={<CameraIcon />}
              label={t.photo.title}
              hint={t.photo.hint}
              onClick={() => setView("photo")}
            />
            <BigSourceButton
              icon={<SearchIcon />}
              label={t.addFood.search}
              hint={t.addFood.searchHint}
              onClick={() => setView("search")}
            />
            <BigSourceButton
              icon={<ScanBarcodeIcon />}
              label={t.addFood.barcode}
              hint={t.addFood.barcodeHint}
              onClick={() => setView("barcode")}
            />
          </div>
          {pickOnly ? null : (
          <div className="grid gap-2">
            <SourceButton
              icon={<PencilLineIcon />}
              label={t.addFood.manual}
              hint={t.addFood.manualHint}
              onClick={() => {
                changeOpen(false);
                onManual();
              }}
            />
            <SourceButton
              icon={<CopyIcon />}
              label={t.addFood.copy}
              hint={t.addFood.copyHint}
              onClick={() => setView("copy")}
            />
          </div>
          )}
          {!pickOnly && templates.length > 0 ? (
            <section aria-label={t.addFood.templates}>
              <h3 className="text-xs font-medium text-muted-foreground">{t.addFood.templates}</h3>
              <div className="mt-2 flex flex-wrap gap-2">
                {templates.map((template) => (
                  <Button
                    key={template.id}
                    variant="outline"
                    size="sm"
                    className="rounded-full"
                    disabled={busy}
                    onClick={() =>
                      void addMeals([template], formatTemplate(t.hoy.templateApplied, { name: template.name }))
                    }
                  >
                    {template.name}
                  </Button>
                ))}
              </div>
            </section>
          ) : null}
        </div>
      ) : view === "search" ? (
        <FoodSearch
          onPick={(next) => pick(next, "search")}
          onEstimateQuery={(query) => {
            setEstimateQuery(query);
            setView("photo");
          }}
        />
      ) : view === "photo" ? (
        <PhotoEstimate
          key={estimateQuery ?? ""}
          autoDescription={estimateQuery}
          onEstimate={(estimate) => {
            changeOpen(false);
            onEstimate(estimate);
          }}
          onManual={
            pickOnly
              ? undefined
              : () => {
                  changeOpen(false);
                  onManual();
                }
          }
        />
      ) : view === "barcode" ? (
        <BarcodeScanner
          onFound={(product) => pick(productChoice(product), "barcode")}
          onSearchByName={() => setView("search")}
          onPhoto={() => {
            setEstimateQuery(null);
            setView("photo");
          }}
        />
      ) : view === "portion" && choice ? (
        <PortionPicker
          choice={choice}
          onAdd={(grams) => {
            rememberFood(choice);
            const ingredient = foodToIngredient(choice.name, choice.per100g, grams);
            changeOpen(false);
            onFoodPicked(ingredient);
          }}
        />
      ) : (
        <CopyFromDay
          targetDay={day}
          busy={busy}
          onCopy={(meals) =>
            void addMeals(
              meals,
              formatTemplate(t.addFood.copied, { n: meals.length, day: formatDateKeyShort(day) }),
            )
          }
        />
      )}
    </Sheet>
  );
}

function BigSourceButton({
  icon,
  label,
  hint,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  hint: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex flex-col items-center gap-1 rounded-xl border bg-primary/5 px-2 py-4 text-center transition-colors hover:bg-accent [&_svg]:size-6 [&_svg]:text-primary"
    >
      {icon}
      <span className="text-sm font-medium">{label}</span>
      <span className="text-[11px] leading-tight text-muted-foreground">{hint}</span>
    </button>
  );
}

function SourceButton({
  icon,
  label,
  hint,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  hint: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3 rounded-xl border px-4 py-3 text-left transition-colors hover:bg-accent [&_svg]:size-5 [&_svg]:shrink-0 [&_svg]:text-muted-foreground"
    >
      {icon}
      <span className="min-w-0">
        <span className="block text-sm font-medium">{label}</span>
        <span className="block text-xs text-muted-foreground">{hint}</span>
      </span>
    </button>
  );
}

function CopyFromDay({
  targetDay,
  busy,
  onCopy,
}: {
  targetDay: string;
  busy: boolean;
  onCopy: (meals: MealDTO[]) => void;
}) {
  const [sourceDay, setSourceDay] = useState(() => addDaysToKey(targetDay, -1));
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const mealsRes = useCachedResource<MealDTO[]>(`meals:${sourceDay}:${sourceDay}`, () =>
    api.listMeals(sourceDay, sourceDay),
  );
  const meals = mealsRes.data ?? null;

  function toggle(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const chosen = (meals ?? []).filter((meal) => selected.has(meal.id));

  return (
    <div className="space-y-3">
      <div className="flex justify-center">
        <DayNavigator
          value={sourceDay}
          onChange={(next) => {
            setSourceDay(next);
            setSelected(new Set());
          }}
        />
      </div>
      {meals === null ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t.common.loading}</p>
      ) : meals.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t.addFood.copyEmpty}</p>
      ) : (
        <ul className="divide-y overflow-hidden rounded-xl border">
          {meals.map((meal) => (
            <li key={meal.id}>
              <label className="flex cursor-pointer items-center gap-3 px-3 py-2.5">
                <input
                  type="checkbox"
                  className="size-4 accent-[var(--primary)]"
                  checked={selected.has(meal.id)}
                  onChange={() => toggle(meal.id)}
                />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">{meal.title}</span>
                <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                  {formatNumber(meal.resolvedCalories)} kcal · {formatNumber(meal.resolvedProtein, 1)} g
                </span>
              </label>
            </li>
          ))}
        </ul>
      )}
      <p className="text-xs text-muted-foreground">
        {formatTemplate(t.addFood.copyTarget, { day: formatDateKeyShort(targetDay) })}
      </p>
      <Button className="w-full" disabled={chosen.length === 0 || busy} onClick={() => onCopy(chosen)}>
        {formatTemplate(t.addFood.copyButton, { n: chosen.length })}
      </Button>
    </div>
  );
}
