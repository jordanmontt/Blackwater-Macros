"use client";

import { useState } from "react";
import { ArrowLeftIcon, CopyIcon, PencilLineIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Sheet } from "@/components/ui/sheet";
import { DayNavigator } from "@/components/meals/day-navigator";
import { api } from "@/lib/api";
import { useCachedResource } from "@/lib/use-cached-resource";
import { addDaysToKey, formatDateKeyShort, formatNumberEs } from "@/lib/core/dates";
import { copyMealPayload } from "@/lib/meal-payload";
import type { MealDTO, MealTemplateDTO } from "@/lib/core/types";
import { formatTemplate, t } from "@/i18n";

type View = "menu" | "copy";

/**
 * «Añadir comida»: every way to add food to `day`, each ending in a saved meal.
 * Escribir a mano opens the review form; Copiar de otro día and the templates
 * add meals directly (with Undo).
 */
export function AddFoodSheet({
  open,
  onOpenChange,
  day,
  templates,
  onManual,
  onAdded,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  day: string;
  templates: MealTemplateDTO[];
  onManual: () => void;
  /** Meals were created on `day` (copies or a template). */
  onAdded: () => void | Promise<unknown>;
}) {
  const [view, setView] = useState<View>("menu");
  const [busy, setBusy] = useState(false);

  function changeOpen(next: boolean) {
    if (!next) setView("menu");
    onOpenChange(next);
  }

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
      title={view === "copy" ? t.addFood.copy : t.addFood.title}
      headerAction={
        view === "copy" ? (
          <Button variant="ghost" size="icon-sm" aria-label={t.addFood.back} onClick={() => setView("menu")}>
            <ArrowLeftIcon />
          </Button>
        ) : undefined
      }
    >
      {view === "menu" ? (
        <div className="space-y-4">
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
          {templates.length > 0 ? (
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
                  {formatNumberEs(meal.resolvedCalories)} kcal · {formatNumberEs(meal.resolvedProtein, 1)} g
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
